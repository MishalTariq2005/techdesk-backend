package com.example.tenant_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;

/**
 * Minimal JWT role extraction for endpoints that need a role check before
 * full triple-layer RBAC (Gateway + AOP + Row-Level Security) lands in
 * section 4.1. Verifies the token with the same HMAC secret auth-service
 * signs with — this is the normal shared-secret pattern for HMAC JWTs
 * across microservices, not a workaround. app.jwt.secret in this service's
 * application.yml must match auth-service's value exactly.
 */
@Component
public class JwtRoleExtractor {

    private final SecretKey key;

    public JwtRoleExtractor(@Value("${app.jwt.secret}") String base64Secret) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(base64Secret));
    }

    /**
     * Returns the role claim from a Bearer token, or null if the header is
     * missing/malformed. Throws io.jsonwebtoken.JwtException (mapped to 401
     * by GlobalExceptionHandler) if a token is present but invalid/expired.
     */
    public String extractRole(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authorizationHeader.substring(7);
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("role", String.class);
    }
}
