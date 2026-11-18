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
 * Calls notification-service's POST /api/notifications for 5.4's
 * ticket-created / ticket-assigned / ticket-resolved / comment-added
 * events. DELIBERATELY fire-and-forget, mirroring asset-service's
 * NotificationClient exactly — a notification-service outage must NEVER
 * fail a ticket create/update/comment action. True at-least-once delivery
 * is Week 4's 6.5 challenge, not this.
 */
@Component
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    private final RestTemplate restTemplate;
    private final SystemTokenService systemTokenService;

    public NotificationClient(RestTemplate restTemplate, SystemTokenService systemTokenService) {
        this.restTemplate = restTemplate;
        this.systemTokenService = systemTokenService;
    }

    public void notify(UUID recipientUserId, String type, String title, String message,
                        String parentType, UUID parentId) {
        if (recipientUserId == null) {
            // e.g. a ticket with no assignee yet — nothing to notify.
            return;
        }
        String authHeader = currentAuthorizationHeader();
        if (authHeader == null) {
            // NEW: previously this meant "skip, log a warning" — which was
            // silently correct for every trigger so far since they all run
            // inside an HTTP request. SlaWarningJob (6.1) is the first
            // caller with no inbound request at all, so it needs its own
            // token instead of forwarding one. See SystemTokenService.
            String tenantId = TenantContext.getCurrentTenant();
            if (tenantId == null) {
                log.warn("No inbound Authorization header and no TenantContext available — skipping notification dispatch");
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
            body.put("recipientUserId", recipientUserId);
            body.put("type", type);
            body.put("title", title);
            body.put("message", message);
            body.put("parentType", parentType);
            body.put("parentId", parentId);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity("http://NOTIFICATION-SERVICE/api/notifications", entity, Void.class);
        } catch (RestClientException e) {
            log.warn("Failed to dispatch notification (type={}, recipient={}) — ticket action still succeeded",
                type, recipientUserId, e);
        }
    }

    private String currentAuthorizationHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        return attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
    }
}