package com.example.auth_service.multitenancy;

import com.example.auth_service.rbac.security.CurrentUserContext;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Schema-based multi-tenancy: every tenant shares the same Postgres
 * database and connection pool, but each gets its own schema. Switching
 * "tenant" is just switching the connection's search_path before handing
 * it to Hibernate, and resetting it before the connection goes back to
 * the pool.
 *
 * IMPORTANT — why this does NOT wrap every connection acquisition in a
 * Spring @Transactional: Hibernate's MultiTenantConnectionProvider SPI
 * calls getConnection()/releaseConnection() directly against the
 * DataSource, outside of Spring's transaction-bound connection
 * management. This is intentional and required by the SPI contract, not
 * a leftover decision — Hibernate needs raw control over exactly which
 * physical connection it's using for a given tenant identifier.
 */
@Component
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;

    public SchemaMultiTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        // Used by Hibernate for tenant-agnostic operations (e.g. schema
        // validation at startup). public is always a safe, neutral default.
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        applySchema(connection, tenantIdentifier);

        // First-checkout verification: confirms the SET search_path above
        // actually took effect before Hibernate does anything with this
        // connection. The TenantVerifyingConnectionProxy wrapper below adds
        // a second, ongoing layer of verification on every subsequent
        // statement — this initial check is what catches a failed/ignored
        // SET search_path immediately, at the cheapest possible point.
        verifyCurrentSchema(connection, tenantIdentifier);

        return TenantVerifyingConnectionProxy.wrap(connection, tenantIdentifier);
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        // Reset to a neutral schema before the connection returns to the
        // pool. Without this, the NEXT request to borrow this physical
        // connection — for a different tenant, or for tenant-agnostic
        // work — would silently inherit the previous tenant's search_path
        // AND the previous caller's role/user-id GUCs (RLS layer 3 would
        // then evaluate against the wrong identity for the next borrower).
        // This is the most important method in this entire file.
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO public");
            stmt.execute("RESET techdesk.user_role");
            stmt.execute("RESET techdesk.current_user_id");
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        throw new UnsupportedOperationException("Cannot unwrap SchemaMultiTenantConnectionProvider");
    }

    private void applySchema(Connection connection, String schemaName) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Identifier, not a bind parameter — cannot be a PreparedStatement
            // placeholder. Safe here because schemaName only ever originates
            // from TenantContext, which is only ever set by
            // TenantIdentifierFilter AFTER TenantLookupService has confirmed
            // it against public.tenants — never directly from raw user input.
            stmt.execute("SET search_path TO \"" + schemaName + "\", public");

            // RBAC layer 3 (RLS, see tenant/V3 migration): stamp the caller's
            // role/user-id onto this connection as session GUCs so the RLS
            // policies on `users` (and any future sensitive tables) can read
            // them via current_setting(). identity is null for tenant-agnostic
            // background work (e.g. Flyway, scheduled jobs) where there is no
            // authenticated caller — RLS policies fall back to their default
            // (deny) behavior in that case, which is the correct/safe default.
            var identity = CurrentUserContext.get();
            if (identity != null) {
                stmt.execute("SET techdesk.user_role = '" + identity.role() + "'");
                stmt.execute("SET techdesk.current_user_id = '" + identity.userId() + "'");
            }
        }
    }

    private void verifyCurrentSchema(Connection connection, String expectedSchema) throws SQLException {
        try (Statement stmt = connection.createStatement();
             var rs = stmt.executeQuery("SELECT current_schema()")) {
            rs.next();
            String actual = rs.getString(1);
            if (!expectedSchema.equals(actual)) {
                throw new TenantIsolationException(
                        "Failed to switch connection to tenant schema '" + expectedSchema +
                        "' — current_schema() reports '" + actual + "'");
            }
        }
    }
}