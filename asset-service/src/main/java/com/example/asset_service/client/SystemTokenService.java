package com.example.asset_service.client;

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
 * Ported from ticket-service's identical class (see its own doc comment for
 * the original SlaWarningJob-driven reasoning). NEW use case here: mints a
 * token for asset-service's OWN internal system actions, distinct from that
 * original "no inbound request at all" scenario — this one specifically
 * handles "the inbound caller DOES have a valid request, but isn't
 * authorized for the internal lookup asset-service itself needs to make on
 * their behalf" (see UserLookupClient.findUsersByRole()'s fix). Forwarding
 * an EMPLOYEE's own JWT to ask auth-service "list every IT_MANAGER" is not
 * something that employee is personally authorized to do — but broadcasting
 * a pending-approval notification to managers is asset-service's OWN
 * action, on its own authority, not something done "as" the requesting
 * employee. A system token expresses that distinction correctly.
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
                .claim("permissions", List.of("USER_VIEW"))
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(TOKEN_TTL_SECONDS, ChronoUnit.SECONDS)))
                .signWith(key)
                .compact();
    }
}
