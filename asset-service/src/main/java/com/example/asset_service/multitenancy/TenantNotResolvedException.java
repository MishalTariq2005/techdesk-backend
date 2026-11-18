package com.example.asset_service.multitenancy;

public class TenantNotResolvedException extends RuntimeException {
    public TenantNotResolvedException(String message) {
        super(message);
    }
}
