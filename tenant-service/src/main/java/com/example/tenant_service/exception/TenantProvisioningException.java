package com.example.tenant_service.exception;

public class TenantProvisioningException extends RuntimeException {
    public TenantProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
