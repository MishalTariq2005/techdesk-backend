package com.example.asset_service.rbac.security;

import com.example.asset_service.multitenancy.TenantContext;
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
 * Same jjwt 0.13.0 fluent parser pattern as auth-service's/ticket-service's
 * versions, needs app.jwt.secret to match auth-service's signing key.
 *
 * Already had the expired-JWT fix. SECURITY FIX (6.4 backport, open item
 * #10 from the Week 3 handoff): adding the tenant-claim cross-check that
 * file-service/notification-service already had and this copy didn't —
 * nothing here previously verified the JWT's OWN tenantId claim matches
 * the tenant this request is actually querying. See file-service's
 * original copy for the fuller writeup of the risk.
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
                try {
                    Claims claims = Jwts.parser().verifyWith(key).build()
                            .parseSignedClaims(token).getPayload();

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
                } catch (JwtException | IllegalArgumentException e) {
                    // Expired / malformed / bad-signature token — fail fast with 401
                    // rather than proceeding unauthenticated or crashing downstream.
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
                    return;
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            CurrentUserContext.clear();
        }
    }
}
