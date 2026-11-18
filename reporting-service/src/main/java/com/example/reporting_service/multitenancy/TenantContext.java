package com.example.reporting_service.multitenancy;

/** Ported from asset-service's copy (itself ported from ticket-service/auth-service) — see the "rule of three" note left in those files; reporting-service is now the 4th. */
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
