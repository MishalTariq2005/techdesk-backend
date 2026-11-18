package com.example.auth_service.multitenancy;

/**
 * Thrown when a JDBC connection's actual schema does not match the schema
 * that was requested for the current tenant context. This should be
 * unreachable in correct operation — it exists as a last-line defense
 * against any code path that somehow bypassed normal tenant resolution
 * (e.g. a leaked pooled connection, a bug in the connection provider).
 */
public class TenantIsolationException extends RuntimeException {
    public TenantIsolationException(String message) {
        super(message);
    }
}
