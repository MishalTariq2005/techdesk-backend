package com.example.auth_service.rbac.security;

import com.example.auth_service.multitenancy.TenantContext;
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
 * Runs after TenantIdentifierFilter (see MultiTenancyConfig ordering — register
 * this with a slightly lower precedence than TenantIdentifierFilter, both still
 * ahead of Spring Security's filter chain since SecurityConfig permitAll()s
 * everything and trusts the gateway/JWT for authz).
 *
 * Same jjwt 0.13.0 fluent parser pattern already used in JwtService —
 * intentionally duplicated here rather than injecting JwtService, to keep
 * this filter's only responsibility "populate CurrentUserContext" without a
 * circular dependency on the auth flow it's securing.
 *
 * SECURITY FIX (6.4 backport, open items #4 and #10 from the Week 3
 * handoff): this was the last of the original four services (auth, ticket,
 * asset were all missing it) without EITHER the expired-JWT try/catch or
 * the tenant-claim cross-check that file-service/notification-service
 * already had. Both applied here now, same as ticket-service's and
 * asset-service's copies. Doesn't affect /api/auth/login itself (no
 * Authorization header present yet on that request) — matters for
 * auth-service's OTHER endpoints that now require a bearer token, like
 * GET /api/users/{id} added in 5.1 for asset-service's UserLookupClient.
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

                String tokenTenantId = claims.get("tenantId", String.class);
                String requestTenantId = TenantContext.getCurrentTenant();
                if (tokenTenantId != null && requestTenantId != null && !tokenTenantId.equals(requestTenantId)) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "JWT tenant does not match request tenant");
                    return;
                }

                String userId = claims.getSubject();
                String role = claims.get("role", String.class);
                @SuppressWarnings("unchecked")
                List<String> perms = claims.get("permissions", List.class);
                Set<String> permissionSet = perms == null
                        ? Set.of()
                        : perms.stream().collect(Collectors.toUnmodifiableSet());

                CurrentUserContext.set(new CurrentUserContext.Identity(userId, role, permissionSet));
            }
            filterChain.doFilter(request, response);
        } finally {
            CurrentUserContext.clear();
        }
    }
}
