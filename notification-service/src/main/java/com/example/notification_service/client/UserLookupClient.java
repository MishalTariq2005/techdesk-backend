package com.example.notification_service.client;

import com.example.notification_service.multitenancy.TenantContext;
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
 * Calls auth-service's GET /api/users/{id} — same forwarded-JWT pattern as
 * asset-service's original UserLookupClient, and DELIBERATELY the same
 * fail-OPEN philosophy (unlike file-service's TicketLookupClient/
 * GadgetRequestLookupClient, which fail closed because they gate real
 * access control). Here, a failed lookup only means "skip sending the
 * email for this notification" — the DB row still gets created and the SSE
 * push still happens regardless (see NotificationDispatchService), so
 * there's nothing security-sensitive riding on this succeeding.
 */
@Component
public class UserLookupClient {

    private static final Logger log = LoggerFactory.getLogger(UserLookupClient.class);

    private final RestTemplate restTemplate;

    public UserLookupClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Optional<UserLookupResponse> findUserById(UUID userId) {
        String authHeader = currentAuthorizationHeader();
        if (authHeader == null) {
            log.warn("No inbound Authorization header available to forward for user lookup {}", userId);
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
            ResponseEntity<UserLookupResponse> response = restTemplate.exchange(
                "http://AUTH-SERVICE/api/users/" + userId,
                HttpMethod.GET, entity, UserLookupResponse.class);
            return Optional.ofNullable(response.getBody());
        } catch (RestClientException e) {
            log.warn("User lookup failed for {} — skipping email send for this notification", userId, e);
            return Optional.empty();
        }
    }

    private String currentAuthorizationHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        return attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
    }

    public record UserLookupResponse(UUID id, String email, String firstName, String lastName) {}
}
