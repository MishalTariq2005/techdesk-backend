package com.example.asset_service.multitenancy;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Same schema-switching mechanism as auth-service's/ticket-service's version,
 * minus the techdesk.user_role / techdesk.current_user_id GUC-setting —
 * assets and asset_assignment_history don't have RLS policies (no per-row
 * ownership concept the way `users` does). If RLS is ever added to a
 * tenant-service table this service touches, port that piece over too and
 * reuse the SAME GUC names (techdesk.user_role, NOT techdesk.current_role —
 * see the Week 2 gotcha about that being a reserved Postgres keyword).
 */
@Component
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;

    public SchemaMultiTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO \"" + tenantIdentifier + "\", public");
        }
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO public");
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
}
