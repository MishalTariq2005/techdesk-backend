package com.example.tenant_service.dto;

import com.example.tenant_service.entity.Tenant;
import com.example.tenant_service.entity.TenantStatus;

import java.time.Instant;

public class TenantResponse {

    private Long id;
    private String companyName;
    private String schemaName;
    private String adminEmail;
    private TenantStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public static TenantResponse from(Tenant t) {
        TenantResponse r = new TenantResponse();
        r.id = t.getId();
        r.companyName = t.getCompanyName();
        r.schemaName = t.getSchemaName();
        r.adminEmail = t.getAdminEmail();
        r.status = t.getStatus();
        r.createdAt = t.getCreatedAt();
        r.updatedAt = t.getUpdatedAt();
        return r;
    }

    public Long getId() { return id; }
    public String getCompanyName() { return companyName; }
    public String getSchemaName() { return schemaName; }
    public String getAdminEmail() { return adminEmail; }
    public TenantStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
