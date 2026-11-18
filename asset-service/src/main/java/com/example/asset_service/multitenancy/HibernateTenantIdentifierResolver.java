package com.example.asset_service.multitenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class HibernateTenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenant = TenantContext.getCurrentTenant();
        return tenant != null ? tenant : "public"; // boot-time fallback, same as auth-service/ticket-service
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
