package com.example.ticket_service.multitenancy;

public class TenantIsolationException extends RuntimeException {
    public TenantIsolationException(String message) {
        super(message);
    }
}
