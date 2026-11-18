package com.example.ticket_service.multitenancy;

public class TenantNotResolvedException extends RuntimeException {
    public TenantNotResolvedException(String message) {
        super(message);
    }
}
