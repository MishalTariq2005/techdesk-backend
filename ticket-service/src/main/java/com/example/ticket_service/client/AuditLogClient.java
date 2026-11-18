package com.example.ticket_service.client;

import com.example.ticket_service.multitenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Calls audit-log-service's POST /api/audit-logs/events. Fire-and-forget,
 * same reasoning as NotificationClient — an audit-log-service outage must
 * never fail the ticket action being audited. This is the missing piece
 * flagged during Week 4 testing: audit-log-service itself was fully built
 * and tamper-evident, but nothing actually called it. This wires
 * ticket-service's own actions (create, status change) into it.
 *
 * Reuses the exact same auth-forwarding / SystemTokenService fallback
 * pattern as NotificationClient, since both clients need identical
 * behavior: forward the inbound caller's JWT when there is one, mint a
 * short-lived system token when called from a context with no inbound
 * request (e.g. a future scheduled job).
 */
@Component
public class AuditLogClient {

    private static final Logger log = LoggerFactory.getLogger(AuditLogClient.class);

    private final RestTemplate restTemplate;
    private final SystemTokenService systemTokenService;

    public AuditLogClient(RestTemplate restTemplate, SystemTokenService systemTokenService) {
        this.restTemplate = restTemplate;
        this.systemTokenService = systemTokenService;
    }

    public void record(String action, String entityType, UUID entityId, Object oldValue, Object newValue) {
        String authHeader = currentAuthorizationHeader();
        if (authHeader == null) {
            String tenantId = TenantContext.getCurrentTenant();
            if (tenantId == null) {
                log.warn("No inbound Authorization header and no TenantContext available — skipping audit log dispatch for action={}", action);
                return;
            }
            authHeader = "Bearer " + systemTokenService.mintSystemToken(tenantId);
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, authHeader);
            String tenantId = TenantContext.getCurrentTenant();
            if (tenantId != null) {
                headers.set("X-Tenant-ID", tenantId);
            }
            headers.set(HttpHeaders.CONTENT_TYPE, "application/json");

            Map<String, Object> body = new HashMap<>();
            body.put("action", action);
            body.put("entityType", entityType);
            body.put("entityId", entityId);
            body.put("oldValue", oldValue);
            body.put("newValue", newValue);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity("http://AUDIT-LOG-SERVICE/api/audit-logs/events", entity, Void.class);
        } catch (RestClientException e) {
            log.warn("Failed to dispatch audit log (action={}, entityId={}) — ticket action still succeeded",
                action, entityId, e);
        }
    }

    private String currentAuthorizationHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        return attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
    }
}
