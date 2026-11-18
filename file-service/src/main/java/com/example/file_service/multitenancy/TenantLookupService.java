package com.example.file_service.multitenancy;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Queries public.tenants directly — same physical Postgres instance/database
 * as tenant-service, just a different service's connection to it. This is
 * intentional duplication of a lookup tenant-service also does, not a shared
 * call between services — keeps asset-service able to validate a tenant
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
     * NEW — not present in ticket-service's version of this file. Needed by
     * WarrantyExpiryJob, which (unlike every other piece of code in this
     * project so far) runs on a @Scheduled thread with no inbound HTTP
     * request and therefore no X-Tenant-ID to resolve a single tenant from.
     * It has to iterate every tenant itself. Excludes SUSPENDED tenants for
     * the same reason validateActiveTenant() rejects them on the request
     * path — the schema still physically exists, but shouldn't be actively
     * processed by a background job.
     */
    public List<String> listActiveTenantSchemas() {
        String sql = "SELECT schema_name FROM public.tenants WHERE status = 'ACTIVE'";
        return jdbcTemplate.queryForList(sql, String.class);
    }
}
