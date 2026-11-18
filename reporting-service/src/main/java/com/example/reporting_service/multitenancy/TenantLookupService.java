package com.example.reporting_service.multitenancy;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
        } catch (EmptyResultDataAccessException e) {
            throw new TenantNotResolvedException("Unknown tenant schema: " + schemaName);
        }
        if ("SUSPENDED".equals(status)) {
            throw new TenantNotResolvedException("Tenant is suspended: " + schemaName);
        }
    }
}
