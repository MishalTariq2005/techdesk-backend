package com.example.asset_service.client;

import com.example.asset_service.multitenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

/**
 * Calls notification-service's POST /api/notifications to record a
 * gadget-request event (approved/rejected). DELIBERATELY fire-and-forget:
 * a notification-service outage must NEVER fail a gadget-request approval
 * or rejection — this is the opposite failure posture from
 * TicketLookupClient/GadgetRequestLookupClient in file-service (which fail
 * closed because they gate access control). Here there's nothing
 * security-sensitive riding on this succeeding; worst case, the requester
 * just doesn't get notified.
 *
 * True at-least-once delivery (a retry queue surviving notification-service
 * being down) is explicitly Week 4's 6.5 challenge, not built here — this
 * is a synchronous best-effort call, caught and logged, nothing more.
 */
@Component
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    private final RestTemplate restTemplate;

    public NotificationClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void notify(UUID recipientUserId, String type, String title, String message,
                        String parentType, UUID parentId) {
        String authHeader = currentAuthorizationHeader();
        if (authHeader == null) {
            log.warn("No inbound Authorization header available to forward for notification dispatch — skipping");
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, authHeader);
            String tenantId = TenantContext.getCurrentTenant();
            if (tenantId != null) {
                headers.set("X-Tenant-ID", tenantId);
            }
            headers.set(HttpHeaders.CONTENT_TYPE, "application/json");

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("recipientUserId", recipientUserId);
            body.put("type", type);
            body.put("title", title);
            body.put("message", message);
            body.put("parentType", parentType);
            body.put("parentId", parentId);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity("http://NOTIFICATION-SERVICE/api/notifications", entity, Void.class);
        } catch (RestClientException e) {
            // Best-effort — caught here, not rethrown. See class doc comment.
            log.warn("Failed to dispatch notification (type={}, recipient={}) — gadget request action still succeeded",
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