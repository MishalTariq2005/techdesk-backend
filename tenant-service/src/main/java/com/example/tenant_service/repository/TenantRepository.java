package com.example.tenant_service.repository;

import com.example.tenant_service.entity.Tenant;
import com.example.tenant_service.entity.TenantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    boolean existsByCompanyNameIgnoreCase(String companyName);

    boolean existsBySchemaName(String schemaName);

    Optional<Tenant> findBySchemaName(String schemaName);

    Page<Tenant> findByStatus(TenantStatus status, Pageable pageable);
}
