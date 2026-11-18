package com.example.file_service.client;

import com.example.file_service.multitenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

/**
 * Calls ticket-service's GET /api/tickets/{id}, same forwarded-JWT pattern
 * as asset-service's UserLookupClient — but a DELIBERATELY OPPOSITE failure
 * philosophy. UserLookupClient is best-effort enrichment for a display
 * string; this client's result GATES ACCESS CONTROL (see
 * FileAccessControlService). Any failure here — 403 from ticket-service
 * (caller lacks even TICKET_VIEW_OWN/ALL), 404 (ticket doesn't exist),
 * network failure, anything — MUST be treated as "cannot verify access,
 * deny," never as "fall back to a permissive default." Fail closed, not
 * fail open. This is the single most important line in this class.
 *
 * Forwarding the caller's own JWT means ticket-service's own visibility
 * gate (getAndCheckVisibility, TICKET_VIEW_ALL/OWN) applies first — if that
 * denies, this returns empty and FileAccessControlService denies too. If it
 * allows, FileAccessControlService still applies its OWN tighter check
 * (creator/assignee/manager specifically) on top, since "can view this
 * ticket" and "can download its attachments" aren't necessarily the same
 * scope — TICKET_VIEW_OWN's actual breadth isn't something this service can
 * assume without ticket-service's source, so both layers run regardless.
 */
@Component
public class TicketLookupClient {

    private static final Logger log = LoggerFactory.getLogger(TicketLookupClient.class);

    private final RestTemplate restTemplate;

    public TicketLookupClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Optional<TicketSummary> findTicketById(UUID ticketId) {
        String authHeader = currentAuthorizationHeader();
        if (authHeader == null) {
            log.warn("No inbound Authorization header available to forward for ticket lookup {}", ticketId);
            return Optional.empty();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, authHeader);
            String tenantId = TenantContext.getCurrentTenant();
            if (tenantId != null) {
                headers.set("X-Tenant-ID", tenantId);
            }
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<TicketDetailWrapper> response = restTemplate.exchange(
                "http://TICKET-SERVICE/api/tickets/" + ticketId,
                HttpMethod.GET, entity, TicketDetailWrapper.class);
            TicketDetailWrapper body = response.getBody();
            return body == null ? Optional.empty() : Optional.ofNullable(body.ticket());
        } catch (RestClientException e) {
            // Covers 403 (caller lacks ticket-view permission entirely),
            // 404 (ticket doesn't exist), and genuine network/service
            // failures — ALL treated identically as "deny," per this
            // class's fail-closed contract.
            log.warn("Ticket lookup failed for {} — denying file access (fail closed)", ticketId, e);
            return Optional.empty();
        }
    }

    private String currentAuthorizationHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        return attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
    }

    /**
     * ⚠ ASSUMPTION, pending confirmation against the real
     * TicketDetailResponse.java: GET /api/tickets/{id} actually returns
     * TicketDetailResponse, built as
     * "new TicketDetailResponse(ticketService.toResponse(ticket), comments, history)"
     * per TicketController — implying a wrapper record with the ticket
     * nested under a field, almost certainly named "ticket" by convention.
     * If TicketDetailResponse.java turns up a different field name, only
     * this record needs to change — everything else in this class is
     * agnostic to the wrapper's exact shape.
     */
    private record TicketDetailWrapper(TicketSummary ticket) {}

    /**
     * Note this hits GET /api/tickets/{id}, whose actual response shape is
     * TicketDetailResponse (ticket + comments + history) per
     * TicketController — but Jackson only binds the fields present in this
     * record and ignores the rest, so only pulling createdBy/assignedTo out
     * of a larger payload is safe without needing every field mirrored here.
     */
    public record TicketSummary(UUID id, UUID createdBy, UUID assignedTo) {}
}
