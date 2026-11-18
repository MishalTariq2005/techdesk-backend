package com.example.notification_service.multitenancy;

public class TenantIsolationException extends RuntimeException {
    public TenantIsolationException(String message) {
        super(message);
    }
}
