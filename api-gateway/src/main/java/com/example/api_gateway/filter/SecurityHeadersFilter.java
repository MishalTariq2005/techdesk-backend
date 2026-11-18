package com.example.api_gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 6.4: "Add Spring Security headers: X-Content-Type-Options, X-Frame-Options,
 * Strict-Transport-Security, Content-Security-Policy." Applied at the
 * gateway (the single edge every browser request passes through) rather
 * than duplicated in each of the eight downstream services.
 *
 * Uses beforeCommit() rather than setting headers inline before
 * chain.filter(): in WebFlux, headers can still be mutated by the proxied
 * response after this filter runs but before the response is actually
 * sent — registering via beforeCommit guarantees these are the LAST word
 * on these specific headers, added just before the response commits,
 * regardless of what a downstream service's own response headers already
 * contained.
 */
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse response = exchange.getResponse();
        response.beforeCommit(() -> {
            var headers = response.getHeaders();
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("X-Frame-Options", "DENY");
            // Only meaningful over HTTPS; harmless to send over plain HTTP
            // in local dev (browsers ignore it for non-TLS origins).
            headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            // 'self' only — this is a JSON API, not a page-serving origin,
            // so a strict default-src covers it without needing script/style
            // exceptions the way a server-rendered frontend would.
            headers.set("Content-Security-Policy", "default-src 'self'");
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE; // right before response commits, after everything else ran
    }
}
