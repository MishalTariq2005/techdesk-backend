package com.example.reporting_service.multitenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TenantIdentifierFilter extends OncePerRequestFilter {

    private final TenantLookupService tenantLookupService;

    public TenantIdentifierFilter(TenantLookupService tenantLookupService) {
        this.tenantLookupService = tenantLookupService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
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
