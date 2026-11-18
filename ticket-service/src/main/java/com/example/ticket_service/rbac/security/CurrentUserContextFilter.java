package com.example.ticket_service.rbac.security;

import com.example.ticket_service.multitenancy.TenantContext;
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
 * SECURITY FIX (6.4 backport, was open item #4 and #10 in the Week 3
 * handoff): this copy was missing BOTH fixes that file-service/
 * notification-service already had —
 *
 * 1. The expired-JWT try/catch: an expired or malformed token used to fall
 *    through to parseSignedClaims() throwing uncaught, surfacing as a raw
 *    500 instead of a 401. The Week 2 handoff notes claimed this was
 *    already fixed here; tracing the actual file showed it wasn't.
 *
 * 2. The tenant-claim cross-check: nothing here previously verified that
 *    the JWT's OWN tenantId claim matches the tenant this request is
 *    actually querying (X-Tenant-ID / TenantContext). A JWT legitimately
 *    issued for tenant B, presented with a hand-set X-Tenant-ID for tenant
 *    A — trivial by calling ticket-service directly on its exposed port,
 *    bypassing the gateway (open item #3) — would previously be accepted.
 *    See file-service's original copy of this filter for the fuller
 *    writeup of the risk.
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
