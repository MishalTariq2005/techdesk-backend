package com.example.auth_service.multitenancy;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the roadmap's literal 3.4 requirement: "Write an
 * integration test proving that two simultaneous requests with different
 * tenantIds query isolated schemas."
 *
 * NAMED *IT* (not *Test*) deliberately — standard Maven Failsafe/Surefire
 * convention to exclude it from the default `mvn test` unit-test run,
 * since this needs a real running Postgres, not a mock. It does NOT spin
 * up a Spring context either — it drives SchemaMultiTenantConnectionProvider
 * directly against raw JDBC, which is enough to prove the actual claim
 * (schema isolation at the connection level) without the overhead/fragility
 * of a full integration-test Spring Boot context.
 *
 * PREREQUISITES TO RUN:
 *   1. `docker compose up -d` from the project root (needs postgres running
 *      on localhost:5432, matching docker-compose.yml's port mapping).
 *   2. Both tenant_acme_corp and tenant_globex_inc schemas must already
 *      exist with their users table — i.e. run this AFTER you've already
 *      created both tenants via 3.3's POST /api/tenants (which you've
 *      already done earlier this session). If you used different tenant
 *      names, update TENANT_A / TENANT_B below to match.
 *
 * Run manually with:
 *   mvn test -Dtest=TenantSchemaIsolationIT
 */
class TenantSchemaIsolationIT {

    private static final String TENANT_A = "tenant_acme_corp";
    private static final String TENANT_B = "tenant_globex_inc";

    private static PGSimpleDataSource buildDataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setServerNames(new String[] { "localhost" });
        ds.setPortNumbers(new int[] { 5432 });
        ds.setDatabaseName("techdesk");
        ds.setUser("techdesk_admin");
        ds.setPassword("changeme");
        return ds;
    }

    @Test
    void twoSimultaneousTenantConnectionsQueryIsolatedSchemas() throws Exception {
        SchemaMultiTenantConnectionProvider provider =
                new SchemaMultiTenantConnectionProvider(buildDataSource());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> resultA = executor.submit(() -> queryCurrentSchemaAndCount(provider, TENANT_A));
            Future<String> resultB = executor.submit(() -> queryCurrentSchemaAndCount(provider, TENANT_B));

            String currentSchemaA = resultA.get(10, TimeUnit.SECONDS);
            String currentSchemaB = resultB.get(10, TimeUnit.SECONDS);

            // The core isolation claim: two concurrent connections, each
            // requested for a different tenant, genuinely land on different
            // physical schemas — not just different in-memory state.
            assertThat(currentSchemaA).isEqualTo(TENANT_A);
            assertThat(currentSchemaB).isEqualTo(TENANT_B);
            assertThat(currentSchemaA).isNotEqualTo(currentSchemaB);

        } finally {
            executor.shutdown();
        }
    }

    @Test
    void requestingNonexistentSchemaIsCaughtByVerification() {
        SchemaMultiTenantConnectionProvider provider =
                new SchemaMultiTenantConnectionProvider(buildDataSource());

        // Postgres accepts `SET search_path` to a schema name that doesn't
        // exist — it does NOT error. That's exactly the gap this proxy's
        // verification step exists to close: without it, code could
        // silently fall through to whatever schema search_path resolves
        // to next (typically "public"), querying/writing the wrong
        // tenant's table without any error at all. This test proves that
        // gap is actually closed: a bogus tenant identifier is rejected
        // at connection-checkout time, not allowed to fail silently.
        String bogusSchema = "tenant_does_not_exist_" + System.nanoTime();

        assertThatThrownBy(() -> provider.getConnection(bogusSchema))
                .isInstanceOf(TenantIsolationException.class)
                .hasMessageContaining(bogusSchema);
    }

    private static String queryCurrentSchemaAndCount(
            SchemaMultiTenantConnectionProvider provider, String tenantSchema) throws Exception {
        Connection connection = provider.getConnection(tenantSchema);
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_schema()")) {
            rs.next();
            return rs.getString(1);
        } finally {
            provider.releaseConnection(tenantSchema, connection);
        }
    }
}
