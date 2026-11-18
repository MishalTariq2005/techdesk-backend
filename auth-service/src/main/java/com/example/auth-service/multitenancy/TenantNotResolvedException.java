package com.example.auth_service.multitenancy;

/**
 * Thrown when a request reaches a tenant-scoped repository with no tenant
 * context set — i.e. TenantIdentifierFilter never received an X-Tenant-ID
 * header. Mapped to 400 by GlobalExceptionHandler.
 *
 * Most common cause during local development: calling auth-service
 * directly (e.g. curl/Invoke-RestMethod to localhost:8081) instead of
 * through the API Gateway. The gateway is what normally sets X-Tenant-ID
 * from the subdomain or JWT (3.1's challenge) — hitting auth-service
 * directly bypasses that, so the header must be set manually for direct
 * testing now that User lives in per-tenant schemas.
 */
public class TenantNotResolvedException extends RuntimeException {
    public TenantNotResolvedException(String message) {
        super(message);
    }
}
