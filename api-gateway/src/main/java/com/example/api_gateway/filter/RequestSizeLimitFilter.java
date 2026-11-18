package com.example.api_gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 6.4: "Add request size limits (max 5 MB per request body) to prevent DoS
 * via oversized payloads." Checked against the Content-Length header —
 * cheap, no need to buffer the body to reject an oversized request. A
 * client that lies about Content-Length (or omits it and streams a huge
 * chunked body) isn't caught by this specific check; file-service's own
 * 10MB upload-size enforcement (5.3) is the real backstop for uploads
 * specifically, this is the general gateway-level guard the roadmap asks
 * for on every route.
 */
@Component
public class RequestSizeLimitFilter implements GlobalFilter, Ordered {

    private static final long MAX_REQUEST_BYTES = 5L * 1024 * 1024; // 5 MB

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long contentLength = request.getHeaders().getContentLength();

        if (contentLength > MAX_REQUEST_BYTES) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.PAYLOAD_TOO_LARGE);
            response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
            DataBuffer buffer = response.bufferFactory().wrap(
                    "{\"error\":\"PAYLOAD_TOO_LARGE\",\"message\":\"Request body exceeds the 5MB limit\"}"
                            .getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -200; // earliest — reject oversized requests before any other processing
    }
}
