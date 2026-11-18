package com.example.ticket_service.ticket.service;

import com.example.ticket_service.assignment.service.AutoAssignmentService;
import com.example.ticket_service.client.AuditLogClient;
import com.example.ticket_service.client.NotificationClient;
import com.example.ticket_service.priority.dto.PriorityPreviewRequest;
import com.example.ticket_service.priority.service.PriorityEngineService;
import com.example.ticket_service.rbac.security.CurrentUserContext;
import com.example.ticket_service.sla.service.SlaService;
import com.example.ticket_service.ticket.dto.*;
import com.example.ticket_service.ticket.entity.Ticket;
import com.example.ticket_service.ticket.entity.TicketAssignmentLog;
import com.example.ticket_service.ticket.exception.InvalidStatusTransitionException;
import com.example.ticket_service.ticket.exception.TicketConflictException;
import com.example.ticket_service.ticket.exception.TicketNotFoundException;
import com.example.ticket_service.ticket.repository.TicketAssignmentLogRepository;
import com.example.ticket_service.ticket.repository.TicketRepository;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepository;
    private final TicketAssignmentLogRepository logRepository;
    private final PriorityEngineService priorityEngineService;
    private final TicketStatusTransitionValidator transitionValidator;
    private final AutoAssignmentService autoAssignmentService;
    private final NotificationClient notificationClient;
    private final SlaService slaService;
    private final AuditLogClient auditLogClient;

    public TicketService(TicketRepository ticketRepository,
                          TicketAssignmentLogRepository logRepository,
                          PriorityEngineService priorityEngineService,
                          TicketStatusTransitionValidator transitionValidator,
                          AutoAssignmentService autoAssignmentService,
                          NotificationClient notificationClient,
                          SlaService slaService,
                          AuditLogClient auditLogClient) {
        this.ticketRepository = ticketRepository;
        this.logRepository = logRepository;
        this.priorityEngineService = priorityEngineService;
        this.transitionValidator = transitionValidator;
        this.autoAssignmentService = autoAssignmentService;
        this.notificationClient = notificationClient;
        this.slaService = slaService;
        this.auditLogClient = auditLogClient;
    }

    @Transactional
    public TicketResponse create(CreateTicketRequest request) {
        CurrentUserContext.Identity caller = requireCaller();

        // Run the 4.2 priority engine — this is the "run priority engine"
        // half of the roadmap line "create ticket, run priority engine,
        // trigger auto-assignment". The auto-assignment half is deliberately
        // NOT here yet; assignedTo stays null until 4.4 exists.
        var priorityResult = priorityEngineService.evaluate(
            new PriorityPreviewRequest(request.categoryId(), request.description(), request.reporterDepartment())
        );

        Ticket ticket = new Ticket(
            request.categoryId(), request.title(), request.description(),
            priorityResult.priority(), UUID.fromString(caller.userId()), request.reporterDepartment()
        );

        // 6.1: compute the resolution SLA due date, business-hours-aware.
        // Not every category has an sla_policies row — computeResolutionDueDate
        // returns null in that case, and the ticket simply has no SLA clock.
        ticket.setSlaDueAt(slaService.computeResolutionDueDate(ticket.getCategoryId(), ticket.getCreatedAt()));

        ticket = ticketRepository.save(ticket);

        logRepository.save(TicketAssignmentLog.statusChange(
            ticket.getId(), ticket.getCreatedBy(), null, "OPEN", "Ticket created"));

        // 4.4: score every available IT staff member and assign the best
        // match, moving the ticket OPEN -> AUTO_ASSIGNED if anyone was
        // available. Writes its own audit log entry (with the full score
        // breakdown) independent of the "ticket created" entry above.
        autoAssignmentService.assign(ticket);

        // 5.4 notification triggers — both fire-and-forget (see
        // NotificationClient's own doc comment). "Ticket created" always
        // fires, notifying the creator their ticket was received. "Ticket
        // assigned" only fires if autoAssignmentService.assign() above
        // actually found a candidate — it mutates the SAME ticket instance
        // in place, so ticket.getAssignedTo() reflects the outcome here
        // without needing autoAssignmentService's own return value.
        notificationClient.notify(ticket.getCreatedBy(), "TICKET_CREATED",
            "Ticket created", "Your ticket \"" + ticket.getTitle() + "\" has been received.",
            "TICKET", ticket.getId());

        if (ticket.getAssignedTo() != null) {
            notificationClient.notify(ticket.getAssignedTo(), "TICKET_ASSIGNED",
                "New ticket assigned to you", "Ticket \"" + ticket.getTitle() + "\" has been assigned to you.",
                "TICKET", ticket.getId());
        }

        // NEW: audit-log-service was fully built (6.3) but nothing actually
        // called it (flagged during Week 4 testing). Wiring ticket creation
        // in first, since it's the highest-value single event to make
        // traceable. Fire-and-forget, same as notifications — an
        // audit-log-service outage must never fail ticket creation itself.
        auditLogClient.record("TICKET_CREATED", "TICKET", ticket.getId(), null, ticket.getStatus());

        return toResponse(ticket);
    }

    public PagedTicketResponse list(String status, String priority, UUID categoryId, UUID assignedTo,
                                     int page, int size) {
        CurrentUserContext.Identity caller = requireCaller();

        Specification<Ticket> spec = (root, query, cb) -> cb.conjunction();

        // Visibility scoping (RBAC layer 2, applied to query results rather
        // than blocking the whole endpoint): TICKET_VIEW_ALL sees everything;
        // TICKET_VIEW_OWN-only callers (EMPLOYEE, IT_STAFF) only ever see
        // tickets they created OR are assigned to. The @RequiresPermission
        // on the controller only guarantees the caller has ONE of these —
        // this is where the actual row-level scoping happens.
        if (!caller.permissions().contains("TICKET_VIEW_ALL")) {
            UUID callerId = UUID.fromString(caller.userId());
            spec = spec.and((root, query, cb) ->
                cb.or(cb.equal(root.get("createdBy"), callerId), cb.equal(root.get("assignedTo"), callerId)));
        }

        if (status != null) {
            String s = status; spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), s));
        }
        if (priority != null) {
            String p = priority; spec = spec.and((root, query, cb) -> cb.equal(root.get("priority"), p));
        }
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("categoryId"), categoryId));
        }
        if (assignedTo != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("assignedTo"), assignedTo));
        }

        Page<Ticket> result = ticketRepository.findAll(spec, PageRequest.of(page, size));
        return new PagedTicketResponse(
            result.getContent().stream().map(this::toResponse).toList(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    public Ticket getAndCheckVisibility(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new TicketNotFoundException("Ticket not found: " + ticketId));

        CurrentUserContext.Identity caller = requireCaller();
        if (!caller.permissions().contains("TICKET_VIEW_ALL")) {
            UUID callerId = UUID.fromString(caller.userId());
            boolean isOwner = ticket.getCreatedBy().equals(callerId)
                || callerId.equals(ticket.getAssignedTo());
            if (!isOwner) {
                throw new TicketNotFoundException("Ticket not found: " + ticketId); // 404, not 403 — avoid confirming existence
            }
        }
        return ticket;
    }

    /**
     * Optimistic locking (4.3 ⚡): no manual version check against a
     * client-supplied value. Ticket.version + @Transactional does the work —
     * if another request updated this same ticket between our findById()
     * and this method's flush, Hibernate throws OptimisticLockException on
     * the UPDATE, which we translate into a 409 carrying the CURRENT state.
     */
    @Transactional
    public TicketResponse updateStatus(UUID ticketId, UpdateStatusRequest request) {
        CurrentUserContext.Identity caller = requireCaller();
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new TicketNotFoundException("Ticket not found: " + ticketId));

        String from = ticket.getStatus();
        String to = request.newStatus().toUpperCase();
        transitionValidator.validate(from, to, caller.role());

        // TEMPORARY — FOR DEMONSTRATING THE OPTIMISTIC-LOCK RACE ONLY.
        // REMOVE THIS BEFORE ANY REAL USE. Widens the window between reading
        // the entity and writing it, so two near-simultaneous requests are
        // guaranteed to both read the SAME stale version before either
        // commits — otherwise, if one request's read/write finishes before
        // the other's read even starts, the second one gets legitimately
        // rejected by transitionValidator.validate() above (using the
        // already-updated status) instead of ever reaching the @Version
        // check below. This sleep makes the true 409 path reproducible
        // on demand instead of depending on network/thread-scheduling luck.
        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

        try {
            ticket.setStatus(to);
            ticket = ticketRepository.saveAndFlush(ticket);
        } catch (OptimisticLockingFailureException | OptimisticLockException e) {
            Ticket current = ticketRepository.findById(ticketId).orElseThrow();
            log.warn("Optimistic lock conflict on ticket {} — caller {} tried {} -> {} but ticket was already at version {}",
                ticketId, caller.userId(), from, to, current.getVersion());
            throw new TicketConflictException(
                "Ticket was modified concurrently — refresh and retry", toResponse(current));
        }

        logRepository.save(TicketAssignmentLog.statusChange(
            ticketId, UUID.fromString(caller.userId()), from, to, request.reason()));

        // NEW: audit-log-service wiring for status changes — the other
        // high-value event to make traceable (who changed what ticket to
        // what status, and when). Same fire-and-forget guarantee.
        auditLogClient.record("TICKET_STATUS_CHANGED", "TICKET", ticketId, from, to);

        log.info("Ticket {} status change: {} -> {} by {} (role={})",
            ticketId, from, to, caller.userId(), caller.role());

        // 5.4 trigger: notify the creator specifically on the transition
        // INTO resolved, not every status change — matches the roadmap's
        // literal "ticket resolved" event, not a generic "status changed"
        // notification for every intermediate state.
        if ("RESOLVED".equals(to)) {
            notificationClient.notify(ticket.getCreatedBy(), "TICKET_RESOLVED",
                "Ticket resolved", "Your ticket \"" + ticket.getTitle() + "\" has been resolved.",
                "TICKET", ticket.getId());
        }

        return toResponse(ticket);
    }

    private CurrentUserContext.Identity requireCaller() {
        CurrentUserContext.Identity identity = CurrentUserContext.get();
        if (identity == null) {
            throw new InvalidStatusTransitionException("No authenticated identity on this request");
        }
        return identity;
    }

    public TicketResponse toResponse(Ticket t) {
        return new TicketResponse(t.getId(), t.getCategoryId(), t.getTitle(), t.getDescription(),
            t.getStatus(), t.getPriority(), t.getCreatedBy(), t.getReporterDepartment(),
            t.getAssignedTo(), t.getVersion(), t.getCreatedAt(), t.getUpdatedAt(),
            t.getSlaDueAt(), t.isSlaBreached());
    }
}
