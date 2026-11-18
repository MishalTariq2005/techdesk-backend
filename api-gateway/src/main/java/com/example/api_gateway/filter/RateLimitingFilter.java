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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 6.4: "max 100 req/min per user, max 10 req/min per IP on auth endpoints."
 *
 * Deliberately a simple in-memory fixed-window counter rather than Spring
 * Cloud Gateway's built-in RequestRateLimiter — that filter is
 * rate-per-second (token bucket via replenishRate/burstCapacity) and needs
 * a Redis reactive client; the roadmap's requirement is phrased in
 * requests-per-MINUTE, which a fixed 60-second window expresses directly
 * and exactly. In-memory is a real scope limitation worth flagging: this
 * only works correctly for a SINGLE api-gateway instance. Running multiple
 * gateway replicas behind a load balancer would need a shared counter
 * (Redis INCR + EXPIRE is the natural next step, and redis is already
 * running in this stack for auth-service/notification-service) — not built
 * here since this project runs one gateway instance.
 */
@Component
public class RateLimitingFilter implements GlobalFilter, Ordered {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final int USER_LIMIT_PER_MINUTE = 100;
    private static final int AUTH_IP_LIMIT_PER_MINUTE = 10;

    private final SecretKey key;
    // NOTE: unbounded growth over the process lifetime (one entry per
    // distinct user/IP ever seen, never evicted) — acceptable for finishing
    // this roadmap's scope, but a real production deployment would want a
    // periodic sweep of stale buckets (windowStart far in the past) or a
    // bounded cache (e.g. Caffeine with expireAfterAccess) instead.
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    }

    private static final class Bucket {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long windowStart;

        Bucket(long now) {
            this.windowStart = now;
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String clientIp = clientIp(request);

        // Stricter per-IP limit specifically on auth endpoints (login,
        // refresh, forgot/reset-password) — these are exactly the endpoints
        // a credential-stuffing or brute-force script would hammer, and an
        // attacker has no per-user JWT yet for the general limiter to key on.
        if (path.startsWith("/api/auth/")) {
            if (isRateLimited("auth-ip:" + clientIp, AUTH_IP_LIMIT_PER_MINUTE)) {
                return reject(exchange, "Too many requests to auth endpoints from this IP — try again shortly");
            }
        }

        // General per-user limit (falls back to per-IP if unauthenticated —
        // an anonymous caller shouldn't get an unlimited pass just because
        // they haven't logged in).
        String userKey = resolveUserKey(request, clientIp);
        if (isRateLimited("user:" + userKey, USER_LIMIT_PER_MINUTE)) {
            return reject(exchange, "Rate limit exceeded — try again shortly");
        }

        return chain.filter(exchange);
    }

    private boolean isRateLimited(String bucketKey, int limit) {
        long now = System.currentTimeMillis();
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> new Bucket(now));

        synchronized (bucket) {
            if (now - bucket.windowStart >= WINDOW_MILLIS) {
                bucket.windowStart = now;
                bucket.count.set(0);
            }
            return bucket.count.incrementAndGet() > limit;
        }
    }

    private String resolveUserKey(ServerHttpRequest request, String fallbackIp) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                Claims claims = Jwts.parser().verifyWith(key).build()
                        .parseSignedClaims(authHeader.substring(7)).getPayload();
                String sub = claims.getSubject();
                if (sub != null) {
                    return sub;
                }
            } catch (JwtException e) {
                // Invalid/expired token — fall through to IP-based keying.
                // GatewayRoleCheckFilter is the one responsible for actually
                // rejecting bad tokens on protected routes; this filter just
                // needs SOME key to rate-limit against.
            }
        }
        return "ip:" + fallbackIp;
    }

    private String clientIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remote = request.getRemoteAddress();
        return remote != null ? remote.getAddress().getHostAddress() : "unknown";
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add("Content-Type", "application/json");
        DataBuffer buffer = response.bufferFactory().wrap(
                ("{\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"" + message + "\"}")
                        .getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -60; // after TenantResolverFilter (-100), before GatewayRoleCheckFilter (0)
    }
}
