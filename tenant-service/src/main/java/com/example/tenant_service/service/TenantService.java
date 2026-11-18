package com.example.tenant_service.service;

import com.example.tenant_service.dto.CreateTenantRequest;
import com.example.tenant_service.dto.TenantResponse;
import com.example.tenant_service.dto.UpdateTenantStatusRequest;
import com.example.tenant_service.entity.Tenant;
import com.example.tenant_service.entity.TenantStatus;
import com.example.tenant_service.exception.TenantAlreadyExistsException;
import com.example.tenant_service.exception.TenantNotFoundException;
import com.example.tenant_service.exception.TenantProvisioningException;
import com.example.tenant_service.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private final TenantRepository tenantRepository;
    private final SchemaProvisioningService schemaProvisioningService;
    private final TenantAdminProvisioningService adminProvisioningService;
    private final WelcomeEmailService welcomeEmailService;

    public TenantService(TenantRepository tenantRepository,
                          SchemaProvisioningService schemaProvisioningService,
                          TenantAdminProvisioningService adminProvisioningService,
                          WelcomeEmailService welcomeEmailService) {
        this.tenantRepository = tenantRepository;
        this.schemaProvisioningService = schemaProvisioningService;
        this.adminProvisioningService = adminProvisioningService;
        this.welcomeEmailService = welcomeEmailService;
    }

    /**
     * Deliberately NOT @Transactional at this level.
     *
     * Postgres DDL (CREATE SCHEMA, CREATE TABLE) is transactional in
     * principle — but only within a single connection/transaction. Flyway
     * opens its OWN connection from the DataSource and commits its own
     * migration transactions independently of whatever Spring transaction
     * might be active in this method. If this whole method were wrapped in
     * one outer @Transactional, the CREATE SCHEMA call would sit uncommitted
     * on this method's connection while Flyway, on a different connection,
     * tried to migrate into a schema it can't yet see — and would fail with
     * "schema does not exist", even though the schema "exists" from this
     * method's own point of view.
     *
     * So each step below (repository.save, raw JDBC DDL, Flyway.migrate)
     * commits on its own as it completes, and failure is handled with an
     * explicit compensating action (dropSchemaIfExists) rather than relying
     * on transaction rollback to undo schema-level DDL.
     */
    public TenantResponse createTenant(CreateTenantRequest request) {
        if (tenantRepository.existsByCompanyNameIgnoreCase(request.getCompanyName())) {
            throw new TenantAlreadyExistsException(
                    "A tenant already exists with company name: " + request.getCompanyName());
        }

        String schemaName = schemaProvisioningService.deriveSchemaName(request.getCompanyName());
        if (tenantRepository.existsBySchemaName(schemaName)) {
            schemaName = schemaProvisioningService.withUniqueSuffix(schemaName);
        }

        Tenant tenant = new Tenant();
        tenant.setCompanyName(request.getCompanyName());
        tenant.setSchemaName(schemaName);
        tenant.setAdminEmail(request.getAdminEmail());
        tenant.setStatus(TenantStatus.PROVISIONING);

        try {
            // Commits immediately on its own — gives us a durable record that
            // provisioning was attempted, independent of what happens next.
            tenant = tenantRepository.save(tenant);
        } catch (DataIntegrityViolationException e) {
            throw new TenantAlreadyExistsException(
                    "A tenant with this company name or schema name already exists: " + request.getCompanyName());
        }

        try {
            schemaProvisioningService.createSchema(schemaName);
            schemaProvisioningService.runTenantMigrations(schemaName);

            String tempPassword = adminProvisioningService.createDefaultAdmin(
                    schemaName, request.getAdminEmail(), request.getAdminFirstName(), request.getAdminLastName());

            tenant.setStatus(TenantStatus.ACTIVE);
            tenant = tenantRepository.save(tenant);

            try {
                welcomeEmailService.sendWelcomeEmail(request.getAdminEmail(), request.getCompanyName(), tempPassword);
            } catch (Exception emailEx) {
                // Tenant + admin user already exist and are committed — a
                // transient SMTP/MailHog failure shouldn't unwind a
                // successful provisioning. Retry/redelivery is a Week 4
                // notification-resilience concern, not this task's job.
                log.warn("Tenant '{}' provisioned successfully but welcome email to {} failed: {}",
                        schemaName, request.getAdminEmail(), emailEx.getMessage());
            }

        } catch (Exception e) {
            log.error("Tenant provisioning failed for schema '{}', rolling back schema creation", schemaName, e);
            try {
                schemaProvisioningService.dropSchemaIfExists(schemaName);
            } catch (Exception dropEx) {
                log.error("Failed to drop schema '{}' during rollback — manual cleanup required", schemaName, dropEx);
            }
            tenant.setStatus(TenantStatus.FAILED);
            tenant.setFailureReason(e.getMessage());
            tenantRepository.save(tenant);

            throw new TenantProvisioningException(
                    "Tenant provisioning failed for '" + request.getCompanyName() + "', schema rolled back", e);
        }

        return TenantResponse.from(tenant);
    }

    public Page<TenantResponse> listTenants(TenantStatus statusFilter, Pageable pageable) {
        Page<Tenant> page = (statusFilter != null)
                ? tenantRepository.findByStatus(statusFilter, pageable)
                : tenantRepository.findAll(pageable);
        return page.map(TenantResponse::from);
    }

    @Transactional
    public TenantResponse updateStatus(Long tenantId, UpdateTenantStatusRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("No tenant found with id " + tenantId));

        tenant.setStatus(request.getStatus());
        if (request.getReason() != null) {
            tenant.setFailureReason(request.getReason());
        }
        tenant = tenantRepository.save(tenant);
        return TenantResponse.from(tenant);
    }
}
