package com.example.notification_service.rbac.security;

import com.example.notification_service.multitenancy.TenantContext;
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
 * Two fixes layered on top of the original auth-service pattern:
 *
 * 1. The expired-JWT try/catch (documented as already present in
 *    ticket-service's copy per the Week 2 handoff, though tracing the
 *    actual file turned up that it wasn't — see that discussion. Built
 *    correctly here regardless of which upstream copy is "right.")
 *
 * 2. NEW — a tenant-claim cross-check. Every other service validates
 *    X-Tenant-ID against public.tenants (TenantIdentifierFilter) and
 *    separately parses the JWT for identity (this filter) — but nothing
 *    anywhere previously checked that the JWT's OWN tenantId claim
 *    actually matches the tenant the request is querying. That gap means
 *    a JWT legitimately issued for tenant B, presented alongside a
 *    hand-set X-Tenant-ID for tenant A (trivial if calling a service
 *    directly on its exposed port, bypassing the gateway — the
 *    ports-exposed-directly gap flagged since Week 2), would previously be
 *    accepted: Hibernate would just query tenant A's schema using a token
 *    never issued for tenant A. 5.3's cross-tenant-file-access challenge is
 *    exactly this scenario, so it gets fixed here first — flagged as a gap
 *    worth backporting to the other three services too, not something
 *    unique to file-service.
 *
 * Filter ordering (see MultiTenancyConfig): TenantIdentifierFilter runs
 * BEFORE this filter and already populated TenantContext by the time this
 * runs, so comparing against it here is safe.
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
                    // FIX: an expired or otherwise invalid JWT used to fall
                    // through to parseSignedClaims() throwing uncaught,
                    // surfacing as a raw 500 instead of a real 401.
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
                    return;
                }

                String tokenTenantId = claims.get("tenantId", String.class);
                String requestTenantId = TenantContext.getCurrentTenant();
                if (tokenTenantId != null && requestTenantId != null && !tokenTenantId.equals(requestTenantId)) {
                    // NEW: the JWT was issued for a different tenant than
                    // the one this request is querying (via X-Tenant-ID).
                    // Reject outright — this is the specific gap 5.3's
                    // cross-tenant-access challenge is testing for.
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
