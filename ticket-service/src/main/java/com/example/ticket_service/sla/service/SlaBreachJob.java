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
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Marks tickets SLA_BREACHED once their due date has passed and they're
 * still open. Per the roadmap: "SLA breach status must be a separate
 * boolean field — it does not replace the ticket status" — this job never
 * touches ticket.status, only ticket.slaBreached. Same tenant-iteration and
 * self-invocation-@Transactional notes as SlaWarningJob apply here too.
 */
@Component
public class SlaBreachJob {

    private static final Logger log = LoggerFactory.getLogger(SlaBreachJob.class);
    private static final List<String> SLA_EXCLUDED_STATUSES = List.of("RESOLVED", "CLOSED", "CANCELLED");

    private final TicketRepository ticketRepository;
    private final TenantLookupService tenantLookupService;
    private final NotificationClient notificationClient;

    public SlaBreachJob(TicketRepository ticketRepository, TenantLookupService tenantLookupService,
                         NotificationClient notificationClient) {
        this.ticketRepository = ticketRepository;
        this.tenantLookupService = tenantLookupService;
        this.notificationClient = notificationClient;
    }

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void checkBreaches() {
        for (String schema : tenantLookupService.listActiveTenantSchemas()) {
            try {
                TenantContext.setCurrentTenant(schema);
                runForCurrentTenant(schema);
            } catch (Exception e) {
                log.error("SLA breach check failed for tenant {}", schema, e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    protected void runForCurrentTenant(String schema) {
        List<Ticket> newlyBreached = ticketRepository.findNewlyBreached(SLA_EXCLUDED_STATUSES, Instant.now());

        for (Ticket ticket : newlyBreached) {
            ticket.setSlaBreached(true);
            ticketRepository.save(ticket);

            if (ticket.getAssignedTo() != null) {
                notificationClient.notify(ticket.getAssignedTo(), "SLA_BREACHED",
                        "SLA breached",
                        "Ticket \"" + ticket.getTitle() + "\" has breached its resolution SLA (was due "
                                + ticket.getSlaDueAt() + ").",
                        "TICKET", ticket.getId());
            }
            log.warn("[{}] SLA BREACHED: ticket {} (was due {})", schema, ticket.getId(), ticket.getSlaDueAt());
        }
    }
}
