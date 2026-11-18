package com.example.ticket_service.client;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * NEW — discovered while wiring SlaWarningJob (6.1) into NotificationClient:
 * every existing NotificationClient (ticket-service, asset-service) only
 * ever forwards the INBOUND request's own Authorization header. That works
 * for every trigger built so far because they all fire from inside an HTTP
 * request (create ticket, approve gadget request, etc.) — but a @Scheduled
 * job has no inbound request at all, so notify() would silently no-op for
 * every SLA warning (see the log.warn branch it already has for exactly
 * this case).
 *
 * This mints a short-lived, minimally-privileged JWT signed with the same
 * shared secret every service already trusts, specifically for
 * service-initiated (not user-initiated) calls. role=SYSTEM is a role no
 * seeded RBAC role uses and no @RequiresPermission check anywhere grants
 * anything to — it exists only to satisfy CurrentUserContextFilter's
 * signature check and file-service/notification-service's tenant-claim
 * cross-check (the JWT's own tenantId must match the X-Tenant-ID header
 * being sent, which NotificationClient already sets from TenantContext).
 */
@Component
public class SystemTokenService {

    private static final UUID SYSTEM_ACTOR_ID = new UUID(0L, 0L);
    private static final long TOKEN_TTL_SECONDS = 60;

    private final SecretKey key;

    public SystemTokenService(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    }

    public String mintSystemToken(String tenantSchema) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(SYSTEM_ACTOR_ID.toString())
                .claim("role", "SYSTEM")
                .claim("tenantId", tenantSchema)
                .claim("permissions", List.of())
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(TOKEN_TTL_SECONDS, ChronoUnit.SECONDS)))
                .signWith(key)
                .compact();
    }
}
