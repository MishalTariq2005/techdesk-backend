package com.example.auth_service.rbac.security;

import java.util.Set;

/**
 * Request-scoped (ThreadLocal) view of the caller, populated by
 * CurrentUserContextFilter from the JWT. Same pattern as TenantContext
 * from 3.4 — MUST be cleared in the filter's finally block or it will
 * leak between requests on a pooled thread.
 *
 * Consumed by:
 *  - PermissionAspect (RBAC layer 2: @RequiresPermission)
 *  - SchemaMultiTenantConnectionProvider (RBAC layer 3: sets the
 *    techdesk.current_role / techdesk.current_user_id GUCs that the
 *    RLS policies in tenant/V3 read)
 */
public class CurrentUserContext {

    public record Identity(String userId, String role, Set<String> permissions) {}

    private static final ThreadLocal<Identity> HOLDER = new ThreadLocal<>();

    public static void set(Identity identity) {
        HOLDER.set(identity);
    }

    public static Identity get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
