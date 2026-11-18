package com.example.file_service.multitenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Same contract as auth-service's/ticket-service's TenantIdentifierFilter:
 * requires X-Tenant-ID on every request, validates it against
 * public.tenants, populates TenantContext, clears it in finally. Highest
 * precedence — registered in MultiTenancyConfig.
 */
@Component
public class TenantIdentifierFilter extends OncePerRequestFilter {

    private final TenantLookupService tenantLookupService;

    public TenantIdentifierFilter(TenantLookupService tenantLookupService) {
        this.tenantLookupService = tenantLookupService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        // Docker's healthcheck hits /actuator/health with no headers at all —
        // it can't supply a tenant, and shouldn't need one to answer "is this
        // JVM up." Bypass tenant resolution for actuator paths specifically.
        if (request.getRequestURI().startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String tenantId = request.getHeader("X-Tenant-ID");
            if (tenantId == null || tenantId.isBlank()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing X-Tenant-ID header");
                return;
            }
            tenantLookupService.validateActiveTenant(tenantId);
            TenantContext.setCurrentTenant(tenantId);
            filterChain.doFilter(request, response);
        } catch (TenantNotResolvedException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }
}
