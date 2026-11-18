package com.example.audit_log_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Unlike every other service, audit-log-service does NOT run
 * TenantIdentifierFilter / multi-tenant schema switching — it lives in one
 * shared "public" schema (see application.yml), so there's no per-request
 * X-Tenant-ID -> schema resolution to do. This filter only needs to parse
 * the caller's identity (including their tenantId claim, kept as the raw
 * schema-name string) for authorization + query scoping.
 *
 * Includes the expired-JWT try/catch and the same signature-verification
 * discipline as file-service/notification-service's copies — no reason for
 * a newly-built service to reintroduce a gap already found and fixed
 * elsewhere.
 */
@Component
public class CurrentUserContextFilter extends OncePerRequestFilter {

    private final SecretKey key;

    public CurrentUserContextFilter(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);

                Claims claims;
                try {
                    claims = Jwts.parser().verifyWith(key).build()
                            .parseSignedClaims(token).getPayload();
                } catch (JwtException e) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
                    return;
                }

                String userId = claims.getSubject();
                String role = claims.get("role", String.class);
                String tenantId = claims.get("tenantId", String.class);
                @SuppressWarnings("unchecked")
                List<String> perms = claims.get("permissions", List.class);
                Set<String> permissionSet = perms == null
                        ? Set.of()
                        : perms.stream().collect(Collectors.toUnmodifiableSet());

                CurrentUserContext.set(new CurrentUserContext.Identity(userId, role, tenantId, permissionSet));
            }
            filterChain.doFilter(request, response);
        } finally {
            CurrentUserContext.clear();
        }
    }
}
