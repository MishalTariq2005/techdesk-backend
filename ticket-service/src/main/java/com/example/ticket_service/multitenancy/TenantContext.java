package com.example.ticket_service.multitenancy;

/**
 * Ported from auth-service's multitenancy package (3.4) — every service that
 * touches tenant-schema data needs its own copy, since services don't share
 * a JVM/classpath. Consider extracting a shared "techdesk-multitenancy" lib
 * once a 3rd or 4th service needs this same code (rule of three) — not worth
 * the build/versioning overhead for just two services yet.
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
