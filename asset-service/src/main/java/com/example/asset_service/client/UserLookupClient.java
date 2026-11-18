package com.example.asset_service.client;

import com.example.asset_service.multitenancy.TenantContext;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Calls auth-service's GET /api/users/{id} directly via Eureka (see
 * RestTemplateConfig's @LoadBalanced RestTemplate), forwarding the original
 * caller's own Authorization header rather than minting a separate
 * service-to-service credential — auth-service's existing @RequiresPermission
 * ("USER_VIEW") check on that endpoint is reused as-is, no new auth scheme.
 *
 * NOTE: GET /api/users/{id} did not exist in auth-service before this work —
 * it was added alongside asset-service specifically to support this lookup.
 * See auth-service's updated UserController.java / UserManagementService.java.
 *
 * FIX (found live): findUsersByRole() ORIGINALLY also forwarded the inbound
 * caller's own JWT, same as findUserById() — but that broke the very first
 * time an EMPLOYEE (who has no USER_VIEW) triggered it indirectly by
 * creating a gadget request that needs manager notification. Forwarding
 * their token to ask auth-service "list every IT_MANAGER" correctly got
 * rejected with 403 — that lookup isn't something the requesting employee
 * is personally authorized to do. But broadcasting a pending-approval
 * notification is asset-service's OWN action, done on its own authority,
 * not "as" the employee — so findUsersByRole() now always mints a system
 * token via SystemTokenService instead of forwarding the caller's header.
 * findUserById() is UNCHANGED and still forwards the caller's own token —
 * that one really is "can THIS caller see THIS specific user," which
 * should keep respecting whatever the caller is actually authorized to see
 * (e.g. resolving an assignee's name for a 409 message they're already
 * entitled to know about).
 */
@Component
public class UserLookupClient {

    private static final Logger log = LoggerFactory.getLogger(UserLookupClient.class);
    private static final int ROLE_LOOKUP_PAGE_SIZE = 200;

    private final RestTemplate restTemplate;
    private final SystemTokenService systemTokenService;

    public UserLookupClient(RestTemplate restTemplate, SystemTokenService systemTokenService) {
        this.restTemplate = restTemplate;
        this.systemTokenService = systemTokenService;
    }

    /**
     * Best-effort lookup — never throws. Returns empty on any failure
     * (auth-service down, user deleted, network blip, or the original
     * caller genuinely lacking USER_VIEW) so a routine 409 doesn't turn
     * into an unrelated 500. Callers should fall back to displaying the
     * raw employeeId when this returns empty.
     */
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
            log.warn("User lookup failed for {} — falling back to id-only display", userId, e);
            return Optional.empty();
        }
    }

    /**
     * Best-effort — returns an empty list on any failure rather than
     * throwing, same fire-and-forget-friendly contract as findUserById().
     * A caller broadcasting a notification to "every manager" should treat
     * an empty result as "notify no one this time", not as an error to
     * surface to the end user.
     *
     * Uses a minted system token (role=SYSTEM, permissions=[USER_VIEW]),
     * NOT the inbound caller's own token — see class-level doc comment for
     * why. Requires a tenant to be resolvable from TenantContext; returns
     * empty (with a log line) if that's not the case, same defensive
     * posture as NotificationClient/AuditLogClient's own system-token
     * fallbacks elsewhere in this codebase.
     */
    public List<UserLookupResponse> findUsersByRole(String role) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.warn("No TenantContext available — skipping role lookup for role={}", role);
            return List.of();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + systemTokenService.mintSystemToken(tenantId));
            headers.set("X-Tenant-ID", tenantId);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<PagedUserLookupResponse> response = restTemplate.exchange(
                "http://AUTH-SERVICE/api/users?role=" + role + "&size=" + ROLE_LOOKUP_PAGE_SIZE,
                HttpMethod.GET, entity, PagedUserLookupResponse.class);
            PagedUserLookupResponse body = response.getBody();
            return body != null && body.content() != null ? body.content() : List.of();
        } catch (RestClientException e) {
            log.warn("User lookup by role failed for role={} — no notifications will be sent for this role this time", role, e);
            return List.of();
        }
    }

    private String currentAuthorizationHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        return attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
    }

    /** Minimal local shape — only the fields this service actually needs. */
    public record UserLookupResponse(UUID id, String email, String firstName, String lastName) {
        public String fullName() {
            return firstName + " " + lastName;
        }
    }

    /** Mirrors auth-service's PagedUserResponse shape — only the fields needed here. */
    public record PagedUserLookupResponse(List<UserLookupResponse> content, int page, int size,
                                           long totalElements, int totalPages) {
    }
}
