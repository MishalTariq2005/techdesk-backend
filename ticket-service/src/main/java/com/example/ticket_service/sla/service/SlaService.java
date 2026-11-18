package com.example.ticket_service.sla.service;

import com.example.ticket_service.sla.entity.BusinessHours;
import com.example.ticket_service.sla.entity.Holiday;
import com.example.ticket_service.sla.entity.SlaPolicy;
import com.example.ticket_service.sla.repository.BusinessHoursRepository;
import com.example.ticket_service.sla.repository.HolidayRepository;
import com.example.ticket_service.sla.repository.SlaPolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes tickets.sla_due_at at creation time. Only tracks the RESOLUTION
 * SLA (not the response SLA) — response-SLA tracking would need "first
 * response" detection (first non-internal comment, or first status change
 * off OPEN), which is a real feature in its own right and out of scope for
 * what this migration/entity model actually wired up. sla_policies.response_hours
 * is stored and available for a future response-SLA pass; flagging this as
 * a known gap rather than silently pretending it's covered.
 */
@Service
public class SlaService {

    private static final Logger log = LoggerFactory.getLogger(SlaService.class);

    private final SlaPolicyRepository slaPolicyRepository;
    private final BusinessHoursRepository businessHoursRepository;
    private final HolidayRepository holidayRepository;
    private final BusinessHoursCalculator calculator;
    private final ZoneId zoneId;

    public SlaService(SlaPolicyRepository slaPolicyRepository,
                       BusinessHoursRepository businessHoursRepository,
                       HolidayRepository holidayRepository,
                       BusinessHoursCalculator calculator,
                       @Value("${app.sla.zone-id:UTC}") String zoneId) {
        this.slaPolicyRepository = slaPolicyRepository;
        this.businessHoursRepository = businessHoursRepository;
        this.holidayRepository = holidayRepository;
        this.calculator = calculator;
        this.zoneId = ZoneId.of(zoneId);
    }

    /**
     * Returns the computed resolution due date, or null if the ticket's
     * category has no sla_policies row — a ticket in a category nobody
     * configured an SLA for simply has no SLA clock, rather than defaulting
     * to some made-up value.
     */
    public Instant computeResolutionDueDate(UUID categoryId, Instant createdAt) {
        Optional<SlaPolicy> policy = slaPolicyRepository.findByCategoryId(categoryId);
        if (policy.isEmpty()) {
            log.debug("No SLA policy configured for category {} — ticket will have no SLA clock", categoryId);
            return null;
        }

        Map<Integer, LocalTime[]> workingHours = loadWorkingHours();
        if (workingHours.isEmpty()) {
            log.warn("No business_hours configured for this tenant — cannot compute an SLA due date");
            return null;
        }

        Set<LocalDate> holidays = holidayRepository.findAll().stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        return calculator.addBusinessHours(
                createdAt, policy.get().getResolutionHours(), workingHours, holidays, zoneId);
    }

    private Map<Integer, LocalTime[]> loadWorkingHours() {
        Map<Integer, LocalTime[]> map = new HashMap<>();
        for (BusinessHours bh : businessHoursRepository.findAll()) {
            map.put((int) bh.getDayOfWeek(), new LocalTime[]{bh.getStartTime(), bh.getEndTime()});
        }
        return map;
    }
}
