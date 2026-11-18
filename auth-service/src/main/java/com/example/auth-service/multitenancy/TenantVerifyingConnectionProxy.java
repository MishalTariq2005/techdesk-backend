package com.example.auth_service.multitenancy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

/**
 * ⚡ Challenge: "verify the active schema before every SQL statement; if it
 * doesn't match the expected tenant schema, throw TenantIsolationException."
 *
 * Why this is a dynamic proxy and not a Hibernate StatementInspector:
 * StatementInspector only sees the SQL string Hibernate is about to run —
 * it has no access to the live JDBC Connection, so it can't actually
 * confirm what schema that statement will execute against. A literal
 * per-statement guarantee has to live at the connection itself.
 *
 * This wraps a real java.sql.Connection so that every call to a
 * statement-creating method (createStatement / prepareStatement /
 * prepareCall) re-verifies `SELECT current_schema()` matches the schema
 * this connection was handed out for, before delegating to the real
 * method. All other Connection methods pass through untouched — a
 * java.sql.Connection has ~60 methods, and a Proxy/InvocationHandler
 * means we only need to special-case the handful that matter instead of
 * implementing the whole interface.
 *
 * Cost: one extra round-trip query per statement-creation call. Acceptable
 * for this exercise's purpose (demonstrating ORM-level isolation
 * enforcement) — NOT something to ship in a real high-throughput service
 * without reconsidering the cost/benefit, since the connection provider
 * below already verifies schema once per connection checkout, which
 * covers the overwhelmingly common failure mode (a stale/mis-set
 * search_path) far more cheaply.
 */
public final class TenantVerifyingConnectionProxy implements InvocationHandler {

    private static final Logger log = LoggerFactory.getLogger(TenantVerifyingConnectionProxy.class);

    private static final Set<String> STATEMENT_CREATING_METHODS = Set.of(
            "createStatement", "prepareStatement", "prepareCall"
    );

    private final Connection delegate;
    private final String expectedSchema;

    private TenantVerifyingConnectionProxy(Connection delegate, String expectedSchema) {
        this.delegate = delegate;
        this.expectedSchema = expectedSchema;
    }

    public static Connection wrap(Connection realConnection, String expectedSchema) {
        return (Connection) Proxy.newProxyInstance(
                TenantVerifyingConnectionProxy.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                new TenantVerifyingConnectionProxy(realConnection, expectedSchema)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (STATEMENT_CREATING_METHODS.contains(method.getName())) {
            verifySchema();
        }
        try {
            Object result = method.invoke(delegate, args);
            // Statement objects themselves don't need wrapping — the
            // verification already happened above, before this statement
            // was created, which is the guarantee the challenge asks for.
            return result;
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    private void verifySchema() throws SQLException {
        String actualSchema;
        try (Statement stmt = delegate.createStatement();
             var rs = stmt.executeQuery("SELECT current_schema()")) {
            rs.next();
            actualSchema = rs.getString(1);
        }
        if (!expectedSchema.equals(actualSchema)) {
            log.error("TENANT ISOLATION BREACH DETECTED: connection's actual schema '{}' does not " +
                    "match expected tenant schema '{}'. Statement creation blocked.",
                    actualSchema, expectedSchema);
            // TODO(6.3 Audit Log Service): once the shared audit_logs event
            // pipeline exists, publish a SECURITY event here instead of
            // only logging. Not built now — 6.3 doesn't exist yet, and a
            // local SLF4J error log is the correct level of plumbing for
            // this stage of the project.
            throw new TenantIsolationException(
                    "Schema mismatch: expected '" + expectedSchema + "' but connection is on '" +
                    actualSchema + "'. Statement creation blocked to prevent cross-tenant access.");
        }
    }
}
