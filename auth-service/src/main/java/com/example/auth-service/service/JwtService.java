package com.example.auth_service.service;
import com.example.auth_service.multitenancy.TenantContext;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpiryMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secretBase64,
            @Value("${app.jwt.access-token-expiry-minutes:15}") long accessTokenExpiryMinutes) {
        // app.jwt.secret must be base64 of at least 32 raw bytes (256 bits) -
        // HS256 throws WeakKeyException at startup otherwise.
        byte[] keyBytes = Base64.getDecoder().decode(secretBase64);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpiryMs = accessTokenExpiryMinutes * 60 * 1000;
    }

    public String generateAccessToken(UUID userId, String tenantId, String role, List<String> permissions) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiryMs);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("tenantId", com.example.auth_service.multitenancy.TenantContext.getCurrentTenant())
                .claim("role", role)
                .claim("permissions", permissions)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /** Verifies signature + expiry and returns the claims. Throws JwtException on any failure. */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getAccessTokenExpirySeconds() {
        return accessTokenExpiryMs / 1000;
    }
}