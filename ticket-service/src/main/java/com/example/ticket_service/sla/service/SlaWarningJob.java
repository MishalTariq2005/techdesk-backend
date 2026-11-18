package com.example.ticket_service.sla.service;

import com.example.ticket_service.client.NotificationClient;
import com.example.ticket_service.multitenancy.TenantContext;
import com.example.ticket_service.multitenancy.TenantLookupService;
import com.example.ticket_service.ticket.entity.Ticket;
import com.example.ticket_service.ticket.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Runs every 5 minutes per the roadmap, iterating every ACTIVE tenant the
 * same way WarrantyExpiryJob/SlaBreachJob do (no inbound request on a
 * @Scheduled thread — see TenantLookupService.listActiveTenantSchemas()).
 *
 * "Resolution SLA" excludes tickets already RESOLVED/CLOSED/CANCELLED —
 * once a ticket is resolved the resolution clock is no longer meaningfully
 * "at risk," regardless of what happens to it afterward (CLOSED, REOPENED).
 */
@Component
public class SlaWarningJob {

    private static final Logger log = LoggerFactory.getLogger(SlaWarningJob.class);
    private static final List<String> SLA_EXCLUDED_STATUSES = List.of("RESOLVED", "CLOSED", "CANCELLED");
    private static final long WARNING_WINDOW_MINUTES = 60; // "approaching SLA breach (within 1 hour)"

    private final TicketRepository ticketRepository;
    private final TenantLookupService tenantLookupService;
    private final NotificationClient notificationClient;

    public SlaWarningJob(TicketRepository ticketRepository, TenantLookupService tenantLookupService,
                          NotificationClient notificationClient) {
        this.ticketRepository = ticketRepository;
        this.tenantLookupService = tenantLookupService;
        this.notificationClient = notificationClient;
    }

    @Scheduled(fixedRate = 5, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void checkApproachingBreaches() {
        for (String schema : tenantLookupService.listActiveTenantSchemas()) {
            try {
                TenantContext.setCurrentTenant(schema);
                runForCurrentTenant(schema);
            } catch (Exception e) {
                log.error("SLA warning check failed for tenant {}", schema, e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    /**
     * Deliberately NOT @Transactional: called via `this.` from within the
     * same class, which bypasses Spring's AOP proxy entirely and would make
     * an @Transactional annotation here silently do nothing (a real, easy-
     * to-miss Spring gotcha). Not needed anyway — each ticketRepository
     * call is already transactional per-method via Spring Data JPA.
     */
    protected void runForCurrentTenant(String schema) {
        Instant warningCutoff = Instant.now().plus(WARNING_WINDOW_MINUTES, ChronoUnit.MINUTES);
        List<Ticket> atRisk = ticketRepository.findApproachingSlaBreach(SLA_EXCLUDED_STATUSES, warningCutoff);

        for (Ticket ticket : atRisk) {
            ticket.setSlaWarningSent(true);
            ticketRepository.save(ticket);

            if (ticket.getAssignedTo() != null) {
                notificationClient.notify(ticket.getAssignedTo(), "SLA_WARNING",
                        "SLA breach approaching",
                        "Ticket \"" + ticket.getTitle() + "\" is due by " + ticket.getSlaDueAt()
                                + " and is at risk of breaching its SLA.",
                        "TICKET", ticket.getId());
            }
            log.info("[{}] SLA warning fired for ticket {} (due {})", schema, ticket.getId(), ticket.getSlaDueAt());
        }
    }
}
