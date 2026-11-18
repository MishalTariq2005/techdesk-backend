package com.example.auth_service.multitenancy;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Validates an incoming X-Tenant-ID value (a schema name, e.g.
 * "tenant_acme_corp") against tenant-service's public.tenants table before
 * Hibernate is allowed to switch to it.
 *
 * This is a direct JDBC read against a table owned by tenant-service, not
 * auth-service — acceptable here because both services share the same
 * Postgres instance/database, and this is a read-only lookup with no
 * write coupling. The cleaner long-term shape is a real HTTP/Eureka call
 * to tenant-service (or a local cache fed by tenant lifecycle events), but
 * that's more machinery than this step needs — flagging it here rather
 * than building it now, since it's not on this week's critical path.
 *
 * Two things this check buys you that are worth keeping:
 *  1. A forged or stale X-Tenant-ID can't make Hibernate switch to a
 *     schema that was never legitimately provisioned.
 *  2. A SUSPENDED tenant (PATCH /api/tenants/{id}/status from 3.3) is
 *     rejected at the auth layer — suspending a tenant now actually does
 *     something, instead of just sitting in the database unused.
 */
@Service
public class TenantLookupService {

    private final JdbcTemplate jdbcTemplate;

    public TenantLookupService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns true only if schemaName exists in public.tenants with
     * status = 'ACTIVE'. Schema names from CREATE SCHEMA in 3.3 are
     * already sanitized to [a-z0-9_] with a "tenant_" prefix, but this
     * query is parameterized regardless — never string-concatenate
     * caller-supplied values into SQL, even values that look pre-validated.
     */
    public boolean isActiveTenantSchema(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            return false;
        }
        try {
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM public.tenants WHERE schema_name = ?",
                    String.class,
                    schemaName
            );
            return "ACTIVE".equals(status);
        } catch (EmptyResultDataAccessException e) {
            return false;
        }
    }
}
