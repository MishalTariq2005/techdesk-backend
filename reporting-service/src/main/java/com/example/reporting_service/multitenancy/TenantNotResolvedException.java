package com.example.reporting_service.multitenancy;

public class TenantNotResolvedException extends RuntimeException {
    public TenantNotResolvedException(String message) {
        super(message);
    }
}
