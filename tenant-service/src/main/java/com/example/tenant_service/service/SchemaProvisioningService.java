package com.example.tenant_service.service;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SchemaProvisioningService {

    private static final Pattern VALID_SUFFIX = Pattern.compile("^[a-z0-9_]+$");
    private static final int MAX_SUFFIX_LENGTH = 40; // Postgres identifier limit is 63; "tenant_" prefix eats into it

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.tenant.migrations-location:classpath:db/migration/tenant}")
    private String tenantMigrationsLocation;

    public SchemaProvisioningService(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Derives a safe Postgres schema name from free-text company input.
     * Lowercases, strips everything outside [a-z0-9], collapses repeats,
     * truncates, prefixes with "tenant_".
     *
     * This sanitization IS the SQL-injection defense for the schema DDL below:
     * schema/table identifiers can't be bound as JDBC parameters the way row
     * values can, so whatever this method returns is the only thing that's
     * ever concatenated into a CREATE/DROP SCHEMA statement.
     */
    public String deriveSchemaName(String companyName) {
        String normalized = companyName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");

        if (normalized.isBlank()) {
            throw new IllegalArgumentException("companyName produced an empty schema name after sanitization");
        }
        if (normalized.length() > MAX_SUFFIX_LENGTH) {
            normalized = normalized.substring(0, MAX_SUFFIX_LENGTH);
        }

        String schemaName = "tenant_" + normalized;

        if (!VALID_SUFFIX.matcher(normalized).matches()) {
            // Should be unreachable given the sanitization above; defense in depth.
            throw new IllegalArgumentException("Sanitized schema name still invalid: " + schemaName);
        }
        return schemaName;
    }

    /** Appends a short random suffix to resolve a schema-name collision. */
    public String withUniqueSuffix(String baseSchemaName) {
        return baseSchemaName + "_" + UUID.randomUUID().toString().substring(0, 6);
    }

    public void createSchema(String schemaName) {
        jdbcTemplate.execute("CREATE SCHEMA \"" + schemaName + "\"");
    }

    public void dropSchemaIfExists(String schemaName) {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS \"" + schemaName + "\" CASCADE");
    }

    /**
     * Runs the tenant migration set against the given schema using a
     * standalone Flyway instance — separate from the auto-configured one
     * that manages the public schema. Each tenant schema gets its own
     * flyway_schema_history table inside itself, so tenants migrate
     * independently of one another.
     */
    public void runTenantMigrations(String schemaName) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations(tenantMigrationsLocation)
                .load();
        flyway.migrate();
    }
}
