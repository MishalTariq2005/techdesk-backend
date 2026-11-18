package com.example.audit_log_service.service;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The JWT's tenantId claim is the tenant's SCHEMA NAME (per Week 1's "JWT
 * payload must contain ... tenantId (schema name)"), but audit_logs.tenant_id
 * is the numeric public.tenants.id the column FK-references. Every other
 * service sidesteps this because they only ever need the schema name itself
 * (to switch Hibernate's active schema) — audit-log-service is the first
 * service that actually needs the numeric id, since it isn't schema-per-
 * tenant itself (it lives in one shared "public" schema, see application.yml).
 */
@Service
public class TenantResolutionService {

    private final JdbcTemplate jdbcTemplate;

    public TenantResolutionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long resolveTenantId(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM public.tenants WHERE schema_name = ?", Long.class, schemaName);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
