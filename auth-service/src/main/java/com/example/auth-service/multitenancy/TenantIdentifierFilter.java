package com.example.auth_service.multitenancy;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads X-Tenant-ID from the incoming request and sets TenantContext for
 * the duration of the request, so any User repository access picks up the
 * right tenant schema. Cleared in a finally block — non-negotiable, since
 * Tomcat reuses worker threads across requests.
 *
 * Registered with very high precedence (see MultiTenancyConfig) so it runs
 * before Spring Security's filter chain — login itself depends on the
 * tenant being resolved before any repository call happens.
 *
 * IMPORTANT: this is a servlet Filter, not a @RestController — it runs
 * outside Spring MVC's DispatcherServlet, so GlobalExceptionHandler
 * (@RestControllerAdvice) never sees exceptions thrown here. The
 * TenantNotResolvedException case below is handled manually, writing the
 * same JSON error shape GlobalExceptionHandler uses elsewhere, so the
 * client always gets a consistent response regardless of which layer
 * rejected the request.
 *
 * Endpoints that genuinely don't need a tenant (health checks, actuator)
 * are exempted below rather than forcing every single request through
 * tenant validation.
 */
@Component
public class TenantIdentifierFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantIdentifierFilter.class);
    private static final String TENANT_HEADER = "X-Tenant-ID";

    private final TenantLookupService tenantLookupService;
    private final ObjectMapper objectMapper;

    public TenantIdentifierFilter(TenantLookupService tenantLookupService, ObjectMapper objectMapper) {
        this.tenantLookupService = tenantLookupService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String tenantSchema = request.getHeader(TENANT_HEADER);

        if (tenantSchema == null || tenantSchema.isBlank()) {
            writeError(response, "Missing " + TENANT_HEADER + " header. If you're calling " +
                    "auth-service directly instead of through the API Gateway, you must set this " +
                    "header manually to a real tenant schema name (e.g. tenant_acme_corp).");
            return;
        }
        if (!tenantLookupService.isActiveTenantSchema(tenantSchema)) {
            log.warn("Rejected request with unresolvable/inactive tenant schema: {}", tenantSchema);
            writeError(response, "No active tenant found for schema: " + tenantSchema +
                    " (tenant may not exist, or may be SUSPENDED)");
            return;
        }

        try {
            TenantContext.setCurrentTenant(tenantSchema);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void writeError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType("application/json");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
