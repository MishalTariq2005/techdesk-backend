package com.example.auth_service.multitenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Tells Hibernate which tenant (schema) the current session belongs to.
 * Reads from TenantContext, which TenantIdentifierFilter populates once
 * per request before any repository access happens.
 */
@Component
public class HibernateTenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    // Used for tenant-agnostic Hibernate operations (schema validation at
    // boot, etc.) where no request — and therefore no TenantContext — exists.
    private static final String DEFAULT_TENANT = "public";

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenant = TenantContext.getCurrentTenant();
        return tenant != null ? tenant : DEFAULT_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        // true = Hibernate checks that a session being reused matches the
        // CURRENT tenant identifier before letting it be reused, raising
        // an error on mismatch instead of silently reusing a session bound
        // to a different tenant. Costs a cheap check; buys a second,
        // independent layer of isolation enforcement above what the
        // connection proxy already does.
        return true;
    }
}
