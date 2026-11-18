package com.example.auth_service.multitenancy;

/**
 * Holds the current request's tenant schema name in a ThreadLocal.
 *
 * Lifecycle: set once per request by TenantIdentifierFilter (very early in
 * the filter chain, before Spring Security and before any repository
 * access), read by HibernateTenantIdentifierResolver whenever Hibernate
 * opens or reuses a session, and MUST be cleared in the filter's finally
 * block. Tomcat reuses worker threads across requests — an uncleared
 * ThreadLocal here would leak one user's tenant schema into the next
 * unrelated request on the same thread, which is a real cross-tenant data
 * leak, not just a memory leak.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCurrentTenant(String schemaName) {
        CURRENT_TENANT.set(schemaName);
    }

    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
