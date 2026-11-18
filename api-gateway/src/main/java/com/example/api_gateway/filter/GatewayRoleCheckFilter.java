package com.example.api_gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * RBAC layer 1 of 3. Coarse-grained: rejects requests to protected path
 * prefixes if the JWT's role claim isn't in the allowed set. This is a
 * denial-of-obviously-wrong-role check, NOT the source of truth for
 * fine-grained authorization — that's PermissionAspect (layer 2) and
 * Postgres RLS (layer 3) inside each service.
 *
 * Unlike TenantResolverFilter (3.1 challenge), this filter DOES verify the
 * JWT signature, because it's making an allow/deny authorization decision
 * rather than just routing metadata — a forged/unsigned token must not be
 * able to talk its way past a role check.
 *
 * Runs after TenantResolverFilter. Order value chosen to be lower priority
 * (runs later) than tenant resolution but before the route is proxied.
 */
@Component
public class GatewayRoleCheckFilter implements GlobalFilter, Ordered {

    // path-prefix -> roles allowed to call it. Extend as new admin-only
    // routes are added in later weeks.
    private static final Map<String, java.util.Set<String>> PROTECTED_PREFIXES = Map.of(
        "/api/tenants", java.util.Set.of("SUPER_ADMIN"),
        "/api/users/invite", java.util.Set.of("COMPANY_ADMIN"),
        "/api/audit-logs", java.util.Set.of("IT_MANAGER", "COMPANY_ADMIN", "AUDITOR"),
        "/api/reports", java.util.Set.of("IT_MANAGER", "COMPANY_ADMIN", "AUDITOR")
    );

    private final SecretKey key;

    public GatewayRoleCheckFilter(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    }

@Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Write path exemption: AuditLogController.recordEvent() deliberately
        // allows any authenticated caller to log their own action (writing an
        // audit trail entry about your own action isn't the same privilege as
        // reading everyone else's) — see its own doc comment. Only the READ
        // endpoints under /api/audit-logs (list, verify) stay gated to
        // IT_MANAGER/COMPANY_ADMIN/AUDITOR via PROTECTED_PREFIXES below.
        if (path.equals("/api/audit-logs/events")) {
            return chain.filter(exchange);
        }

        String requiredRolesKey = PROTECTED_PREFIXES.keySet().stream()
            .filter(path::startsWith)
            .findFirst()
            .orElse(null);

        if (requiredRolesKey == null) {
            return chain.filter(exchange); // unprotected route, let it through
        }

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return reject(exchange, "Missing bearer token for protected route " + path);
        }

        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(authHeader.substring(7)).getPayload();
            String role = claims.get("role", String.class);

            if (role == null || !PROTECTED_PREFIXES.get(requiredRolesKey).contains(role)) {
                return reject(exchange, "Role '" + role + "' not permitted on " + path);
            }
        } catch (JwtException e) {
            return reject(exchange, "Invalid JWT: " + e.getMessage());
        }

        return chain.filter(exchange);
    }

    private Mono<Void> reject(ServerWebExchange exchange, String reason) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().add("Content-Type", "application/json");
        DataBuffer buffer = response.bufferFactory().wrap(
            ("{\"error\":\"FORBIDDEN\",\"message\":\"" + reason.replace("\"", "'") + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return 0; // after TenantResolverFilter if that filter has a negative/HIGHEST_PRECEDENCE order
    }
}
