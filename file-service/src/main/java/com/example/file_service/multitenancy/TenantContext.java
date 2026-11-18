package com.example.file_service.multitenancy;

/**
 * Ported from ticket-service's multitenancy package, which itself was
 * ported from auth-service's (3.4) — every service that touches
 * tenant-schema data needs its own copy, since services don't share a
 * JVM/classpath. Now a 3rd copy (auth, ticket, asset) — worth revisiting
 * the "extract a shared lib" idea per the rule-of-three note already left
 * in ticket-service's copy of this file.
 */
public class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    public static void setCurrentTenant(String schema) {
        CURRENT_TENANT.set(schema);
    }

    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
