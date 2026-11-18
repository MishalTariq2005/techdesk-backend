package com.example.reporting_service.rbac.security;

import com.example.reporting_service.multitenancy.TenantContext;
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
 * Built with the expired-JWT try/catch AND the tenant-claim cross-check
 * from day one (same posture as file-service/notification-service/
 * audit-log-service) — no reason for a new service to reintroduce a gap
 * that's already been found and fixed three times elsewhere in this
 * project. See file-service's CurrentUserContextFilter for the original,
 * fuller writeup of why the cross-check matters.
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
