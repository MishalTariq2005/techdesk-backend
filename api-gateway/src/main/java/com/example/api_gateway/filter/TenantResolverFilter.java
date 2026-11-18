package com.example.api_gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the current tenant from either a subdomain (e.g. acme-corp.techdesk.com)
 * or a "tenantId" claim inside an Authorization JWT, and forwards it downstream
 * as the X-Tenant-ID header.
 *
 * Resolution order (challenge requirement): subdomain takes priority over JWT.
 * If neither source yields a tenant, the request is passed through unmodified —
 * downstream services decide whether a missing X-Tenant-ID is an error.
 */
@Component
public class TenantResolverFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TenantResolverFilter.class);
    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Matches e.g. "acme-corp.techdesk.com" -> captures "acme-corp".
    // Change the literal "techdesk" if your local/dev domain differs.
    private static final Pattern SUBDOMAIN_PATTERN =
            Pattern.compile("^([a-zA-Z0-9-]+)\\.techdesk\\.com$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String tenantId = resolveFromSubdomain(request);
        String source = "subdomain";

        if (tenantId == null) {
            tenantId = resolveFromJwt(request);
            source = "jwt";
        }

        if (tenantId == null) {
            log.warn("No tenant could be resolved for request {}", request.getURI());
            return chain.filter(exchange);
        }

        log.info("Resolved tenantId='{}' via {}", tenantId, source);

        ServerHttpRequest mutatedRequest = request.mutate()
                .header(TENANT_HEADER, tenantId)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private String resolveFromSubdomain(ServerHttpRequest request) {
        String host = request.getHeaders().getFirst("Host");
        if (host == null) {
            host = request.getURI().getHost();
        }
        if (host == null) {
            return null;
        }
        String hostWithoutPort = host.split(":")[0];
        Matcher matcher = SUBDOMAIN_PATTERN.matcher(hostWithoutPort);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private String resolveFromJwt(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] decoded = base64UrlDecode(parts[1]);
            JsonNode payload = OBJECT_MAPPER.readTree(new String(decoded, StandardCharsets.UTF_8));
            JsonNode tenantNode = payload.get("tenantId");
            return tenantNode != null ? tenantNode.asText() : null;
        } catch (Exception e) {
            log.warn("Failed to parse tenantId from JWT: {}", e.getMessage());
            return null;
        }
    }

    private byte[] base64UrlDecode(String input) {
        // JWT segments are unpadded base64url; Java's decoder wants proper padding.
        String padded = input;
        int mod4 = input.length() % 4;
        if (mod4 > 0) {
            padded += "====".substring(mod4);
        }
        return Base64.getUrlDecoder().decode(padded);
    }

    @Override
    public int getOrder() {
        return -100; // run early, before routing logic that might depend on the header
    }
}