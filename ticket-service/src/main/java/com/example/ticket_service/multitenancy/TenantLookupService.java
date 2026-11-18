package com.example.ticket_service.multitenancy;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Queries public.tenants directly — same physical Postgres instance/database
 * as tenant-service, just a different service's connection to it. This is
 * intentional duplication of a lookup tenant-service also does, not a shared
 * call between services — keeps ticket-service able to validate a tenant
 * without a network hop to tenant-service on every single request.
 */
@Service
public class TenantLookupService {

    private final JdbcTemplate jdbcTemplate;

    public TenantLookupService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void validateActiveTenant(String schemaName) {
        String sql = "SELECT status FROM public.tenants WHERE schema_name = ?";
        String status;
        try {
            status = jdbcTemplate.queryForObject(sql, String.class, schemaName);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new TenantNotResolvedException("Unknown tenant schema: " + schemaName);
        }
        if ("SUSPENDED".equals(status)) {
            throw new TenantNotResolvedException("Tenant is suspended: " + schemaName);
        }
    }

    /**
     * NEW — needed by SlaWarningJob/SlaBreachJob (6.1), same reasoning as
     * asset-service's identical addition for WarrantyExpiryJob: a
     * @Scheduled method has no inbound request/X-Tenant-ID to resolve a
     * single tenant from, so it has to iterate every active tenant itself.
     */
    public java.util.List<String> listActiveTenantSchemas() {
        String sql = "SELECT schema_name FROM public.tenants WHERE status = 'ACTIVE'";
        return jdbcTemplate.queryForList(sql, String.class);
    }
}
