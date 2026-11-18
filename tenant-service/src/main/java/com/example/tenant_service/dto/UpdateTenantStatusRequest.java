package com.example.tenant_service.dto;

import com.example.tenant_service.entity.TenantStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateTenantStatusRequest {

    @NotNull(message = "status is required")
    private TenantStatus status;

    private String reason;

    public TenantStatus getStatus() { return status; }
    public void setStatus(TenantStatus status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
