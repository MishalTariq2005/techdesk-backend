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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Calls asset-service's GET /api/gadget-requests/{id} — same fail-closed
 * contract as TicketLookupClient (see its doc comment): any failure here
 * means "deny," never a graceful fallback.
 *
 * Unlike the ticket case, this endpoint is completely UNGATED in
 * asset-service (see GadgetRequestController — no @RequiresPermission on
 * getById(), by design: an employee needs to view their own request's
 * status). That means ticket-service's own gate does real filtering work
 * for TicketLookupClient, but this one does NONE — 100% of the
 * authorization decision for gadget-request-attached files happens in
 * FileAccessControlService, not upstream.
 *
 * Response shape mirrors GadgetRequestResponse exactly (this service's own
 * DTO from 5.2, so no guessing involved here the way TicketDetailResponse's
 * nested shape required a pause) — Jackson only binds the fields present
 * below and ignores the rest of the actual payload.
 */
@Component
public class GadgetRequestLookupClient {

    private static final Logger log = LoggerFactory.getLogger(GadgetRequestLookupClient.class);

    private final RestTemplate restTemplate;

    public GadgetRequestLookupClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Optional<GadgetRequestSummary> findRequestById(UUID gadgetRequestId) {
        String authHeader = currentAuthorizationHeader();
        if (authHeader == null) {
            log.warn("No inbound Authorization header available to forward for gadget-request lookup {}", gadgetRequestId);
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
            ResponseEntity<GadgetRequestSummary> response = restTemplate.exchange(
                "http://ASSET-SERVICE/api/gadget-requests/" + gadgetRequestId,
                HttpMethod.GET, entity, GadgetRequestSummary.class);
            return Optional.ofNullable(response.getBody());
        } catch (RestClientException e) {
            log.warn("Gadget request lookup failed for {} — denying file access (fail closed)", gadgetRequestId, e);
            return Optional.empty();
        }
    }

    private String currentAuthorizationHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        return attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
    }

    /** Only the fields this service actually needs — matches GadgetRequestResponse's flat shape. */
    public record GadgetRequestSummary(UUID id, UUID gadgetTypeId, String gadgetTypeName,
                                        UUID employeeId, String reason, String status,
                                        UUID assignedAssetId, String rejectionReason,
                                        Instant createdAt, Instant updatedAt) {}
}
