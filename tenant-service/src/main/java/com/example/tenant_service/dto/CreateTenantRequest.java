package com.example.tenant_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class CreateTenantRequest {

    @NotBlank(message = "companyName is required")
    private String companyName;

    @NotBlank(message = "adminEmail is required")
    @Email(message = "adminEmail must be a valid email address")
    private String adminEmail;

    @NotBlank(message = "adminFirstName is required")
    private String adminFirstName;

    @NotBlank(message = "adminLastName is required")
    private String adminLastName;

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getAdminEmail() { return adminEmail; }
    public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }

    public String getAdminFirstName() { return adminFirstName; }
    public void setAdminFirstName(String adminFirstName) { this.adminFirstName = adminFirstName; }

    public String getAdminLastName() { return adminLastName; }
    public void setAdminLastName(String adminLastName) { this.adminLastName = adminLastName; }
}
