package com.example.tenant_service.controller;

import com.example.tenant_service.dto.CreateTenantRequest;
import com.example.tenant_service.dto.TenantResponse;
import com.example.tenant_service.dto.UpdateTenantStatusRequest;
import com.example.tenant_service.entity.TenantStatus;
import com.example.tenant_service.exception.ForbiddenException;
import com.example.tenant_service.security.JwtRoleExtractor;
import com.example.tenant_service.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;
    private final JwtRoleExtractor jwtRoleExtractor;

    public TenantController(TenantService tenantService, JwtRoleExtractor jwtRoleExtractor) {
        this.tenantService = tenantService;
        this.jwtRoleExtractor = jwtRoleExtractor;
    }

    @PostMapping
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        // Left open for now, same as the rest of Week 1's endpoints — tighten
        // this alongside the role checks below once SUPER_ADMIN bootstrap is decided.
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.createTenant(request));
    }

    @GetMapping
    public ResponseEntity<Page<TenantResponse>> listTenants(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false) TenantStatus status,
            Pageable pageable) {
        requireSuperAdmin(authHeader);
        return ResponseEntity.ok(tenantService.listTenants(status, pageable));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TenantResponse> updateStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Long id,
            @Valid @RequestBody UpdateTenantStatusRequest request) {
        requireSuperAdmin(authHeader);
        return ResponseEntity.ok(tenantService.updateStatus(id, request));
    }

    private void requireSuperAdmin(String authHeader) {
        String role = jwtRoleExtractor.extractRole(authHeader);
        if (!"SUPER_ADMIN".equals(role)) {
            throw new ForbiddenException("SUPER_ADMIN role required for this operation");
        }
    }
}
