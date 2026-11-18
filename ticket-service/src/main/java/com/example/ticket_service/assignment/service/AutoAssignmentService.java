package com.example.ticket_service.assignment.service;

import com.example.ticket_service.assignment.dto.CandidateScoreDto;
import com.example.ticket_service.assignment.entity.StaffProfile;
import com.example.ticket_service.assignment.repository.StaffProfileRepository;
import com.example.ticket_service.assignment.repository.StaffSkillRepository;
import com.example.ticket_service.ticket.entity.Ticket;
import com.example.ticket_service.ticket.entity.TicketAssignmentLog;
import com.example.ticket_service.ticket.repository.TicketAssignmentLogRepository;
import com.example.ticket_service.ticket.repository.TicketRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Scoring algorithm per roadmap 4.4:
 *   skillMatchScore (x40) + availabilityScore (x25) + locationMatch (x15)
 *   - workloadPenalty (x5) + priorityExperience (x20)
 *
 * DETERMINISM (roadmap requirement: "same inputs must always produce the
 * same assignment"): every input to scoring is read fresh from the DB inside
 * one transaction, and the final sort breaks ties by staffUserId ascending
 * — without an explicit tie-break, two candidates scoring identically could
 * sort in different orders across runs depending on incidental DB/collection
 * ordering, which would violate determinism even though the SCORES
 * themselves were computed deterministically.
 *
 * ASSUMPTION (flagged, not hidden): "locationMatch" has no existing concept
 * to attach to elsewhere in the system (no office/region tracking exists).
 * Interpreted here as StaffProfile.location vs Ticket.reporterDepartment —
 * full match = 15 points, anything else = 0. Revisit if a real location/
 * office model gets introduced later.
 */
@Service
public class AutoAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AutoAssignmentService.class);

    private static final List<String> TERMINAL_STATUSES = List.of("CLOSED", "CANCELLED");
    private static final List<String> RESOLVED_STATUSES = List.of("RESOLVED", "CLOSED");
    private static final int EXPERIENCE_CAP = 10; // resolved tickets at this priority for a "full" experience score
    private static final int BURST_THRESHOLD = 3;
    private static final int BURST_WINDOW_MINUTES = 30;

    private final StaffProfileRepository staffProfileRepository;
    private final StaffSkillRepository staffSkillRepository;
    private final TicketRepository ticketRepository;
    private final TicketAssignmentLogRepository logRepository;
    private final ObjectMapper objectMapper;

    public AutoAssignmentService(StaffProfileRepository staffProfileRepository,
                                  StaffSkillRepository staffSkillRepository,
                                  TicketRepository ticketRepository,
                                  TicketAssignmentLogRepository logRepository,
                                  ObjectMapper objectMapper) {
        this.staffProfileRepository = staffProfileRepository;
        this.staffSkillRepository = staffSkillRepository;
        this.ticketRepository = ticketRepository;
        this.logRepository = logRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Scores every available candidate, applies the burst fallback, assigns
     * the winner, and writes a full audit trail. Called from
     * TicketService.create() right after a ticket is first saved.
     */
    @Transactional
    public void assign(Ticket ticket) {
        List<StaffProfile> availableProfiles = staffProfileRepository.findByAvailableTrue();

        List<CandidateScoreDto> allScored = availableProfiles.stream()
            .map(profile -> score(profile, ticket))
            .filter(Optional::isPresent)
            .map(Optional::get)
            // Deterministic ordering: score desc, THEN staffUserId asc as a tie-break.
            .sorted(Comparator.comparingDouble(CandidateScoreDto::totalScore).reversed()
                .thenComparing(c -> c.staffUserId().toString()))
            .toList();

        if (allScored.isEmpty()) {
            log.warn("No available IT staff candidates for ticket {} (category={}) — leaving unassigned",
                ticket.getId(), ticket.getCategoryId());
            logRepository.save(TicketAssignmentLog.autoAssignment(
                ticket.getId(), null, ticket.getStatus(), ticket.getStatus(),
                "No available IT staff — left unassigned", writeJson(allScored)));
            return;
        }

        // Walk the sorted list applying the burst fallback (4.4 challenge) —
        // skip anyone who's been auto-assigned 3 tickets in a row within the
        // last 30 minutes, even if they scored highest.
        List<CandidateScoreDto> finalScored = allScored.stream()
            .map(c -> c.skippedForBurst() ? c : maybeMarkBurst(c))
            .toList();

        Optional<CandidateScoreDto> winner = finalScored.stream()
            .filter(c -> !c.skippedForBurst())
            .findFirst();

        String from = ticket.getStatus();
        if (winner.isEmpty()) {
            // Every candidate is mid-burst. Rather than leave an OPEN ticket
            // unassigned during a genuine incident spike, fall back to the
            // single highest scorer anyway — better an overloaded assignee
            // than no assignee. This bypass-of-the-bypass is itself logged.
            CandidateScoreDto fallback = finalScored.get(0);
            log.warn("ALL top candidates for ticket {} are mid-burst — forcing assignment to top scorer {} anyway",
                ticket.getId(), fallback.staffUserId());
            applyAssignment(ticket, fallback.staffUserId(), from, finalScored,
                "All candidates were burst-limited; forced assignment to top scorer as a last resort");
            return;
        }

        applyAssignment(ticket, winner.get().staffUserId(), from, finalScored, "Auto-assigned via scoring algorithm");
    }

    private void applyAssignment(Ticket ticket, UUID staffId, String from, List<CandidateScoreDto> allScored, String reason) {
        ticket.setAssignedTo(staffId);
        ticket.setStatus("AUTO_ASSIGNED");
        ticketRepository.save(ticket);

        logRepository.save(TicketAssignmentLog.autoAssignment(
            ticket.getId(), staffId, from, "AUTO_ASSIGNED", reason, writeJson(allScored)));

        log.info("Ticket {} auto-assigned to {} ({})", ticket.getId(), staffId, reason);
    }

    private CandidateScoreDto maybeMarkBurst(CandidateScoreDto candidate) {
        List<TicketAssignmentLog> recent = logRepository
            .findTop3ByAssignedToAndActorIdIsNullOrderByCreatedAtDesc(candidate.staffUserId());

        boolean burst = recent.size() >= BURST_THRESHOLD
            && recent.get(BURST_THRESHOLD - 1).getCreatedAt().isAfter(Instant.now().minus(BURST_WINDOW_MINUTES, ChronoUnit.MINUTES));

        if (burst) {
            log.warn("Staff {} skipped for ticket assignment: {} auto-assignments in the last {} minutes",
                candidate.staffUserId(), BURST_THRESHOLD, BURST_WINDOW_MINUTES);
        }

        return new CandidateScoreDto(candidate.staffUserId(), candidate.skillMatchScore(), candidate.availabilityScore(),
            candidate.locationMatch(), candidate.workloadPenalty(), candidate.priorityExperience(),
            candidate.totalScore(), burst, candidate.activeTicketCount());
    }

    private Optional<CandidateScoreDto> score(StaffProfile profile, Ticket ticket) {
        long activeCount = ticketRepository.countActiveByAssignedTo(profile.getStaffUserId(), TERMINAL_STATUSES);

        // Availability determination (roadmap: "flag + active ticket count
        // threshold") — a candidate over their own configured capacity is
        // excluded from the pool entirely, not just scored low.
        if (activeCount >= profile.getMaxActiveTickets()) {
            return Optional.empty();
        }

        double skillFraction = staffSkillRepository
            .findByStaffUserIdAndCategoryId(profile.getStaffUserId(), ticket.getCategoryId())
            .map(s -> s.getSkillLevel() / 5.0)
            .orElse(0.0); // no skill row for this category = wrong skill set, scores 0 (still a candidate)
        double skillMatchScore = skillFraction * 40.0;

        double loadRatio = (double) activeCount / profile.getMaxActiveTickets();
        double availabilityScore = (1.0 - loadRatio) * 25.0;

        double locationMatch = (profile.getLocation() != null
            && profile.getLocation().equalsIgnoreCase(ticket.getReporterDepartment())) ? 15.0 : 0.0;

        double workloadPenalty = loadRatio * 5.0;

        long resolvedAtThisPriority = ticketRepository.countResolvedByAssignedToAndPriority(
            profile.getStaffUserId(), ticket.getPriority(), RESOLVED_STATUSES);
        double experienceFraction = Math.min(1.0, (double) resolvedAtThisPriority / EXPERIENCE_CAP);
        double priorityExperience = experienceFraction * 20.0;

        double total = skillMatchScore + availabilityScore + locationMatch - workloadPenalty + priorityExperience;

        return Optional.of(new CandidateScoreDto(profile.getStaffUserId(), skillMatchScore, availabilityScore,
            locationMatch, workloadPenalty, priorityExperience, total, false, activeCount));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("Failed to serialize assignment score breakdown", e);
            return "{\"error\":\"serialization failed\"}";
        }
    }
}
