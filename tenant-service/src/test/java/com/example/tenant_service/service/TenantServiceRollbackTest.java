package com.example.tenant_service.service;

import com.example.tenant_service.dto.CreateTenantRequest;
import com.example.tenant_service.entity.Tenant;
import com.example.tenant_service.entity.TenantStatus;
import com.example.tenant_service.exception.TenantProvisioningException;
import com.example.tenant_service.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TenantServiceRollbackTest {

    private TenantRepository tenantRepository;
    private SchemaProvisioningService schemaProvisioningService;
    private TenantAdminProvisioningService adminProvisioningService;
    private WelcomeEmailService welcomeEmailService;
    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        schemaProvisioningService = mock(SchemaProvisioningService.class);
        adminProvisioningService = mock(TenantAdminProvisioningService.class);
        welcomeEmailService = mock(WelcomeEmailService.class);

        tenantService = new TenantService(
                tenantRepository, schemaProvisioningService, adminProvisioningService, welcomeEmailService);

        // Echoes back whatever entity it was given, assigning an id on first save.
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant t = invocation.getArgument(0);
            if (t.getId() == null) t.setId(1L);
            return t;
        });
        when(schemaProvisioningService.deriveSchemaName(anyString())).thenReturn("tenant_acme");
    }

    private CreateTenantRequest acmeRequest() {
        CreateTenantRequest request = new CreateTenantRequest();
        request.setCompanyName("Acme");
        request.setAdminEmail("admin@acme.test");
        request.setAdminFirstName("Ada");
        request.setAdminLastName("Min");
        return request;
    }

    @Test
    void schemaIsDroppedAndTenantMarkedFailed_whenMigrationFails() {
        doThrow(new RuntimeException("simulated migration failure"))
                .when(schemaProvisioningService).runTenantMigrations("tenant_acme");

        assertThrows(TenantProvisioningException.class, () -> tenantService.createTenant(acmeRequest()));

        // The part @Transactional alone can't give us: CREATE SCHEMA and the
        // Flyway migration run on separate connections, so failure must be
        // cleaned up explicitly rather than relying on rollback semantics.
        verify(schemaProvisioningService).createSchema("tenant_acme");
        verify(schemaProvisioningService).dropSchemaIfExists("tenant_acme");

        verifyNoInteractions(adminProvisioningService);
        verifyNoInteractions(welcomeEmailService);

        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository, times(2)).save(captor.capture());
        Tenant finalState = captor.getAllValues().get(1);
        assertThat(finalState.getStatus()).isEqualTo(TenantStatus.FAILED);
        assertThat(finalState.getFailureReason()).contains("simulated migration failure");
    }

    @Test
    void schemaSurvives_whenOnlyWelcomeEmailFails() {
        doThrow(new RuntimeException("mailhog unreachable"))
                .when(welcomeEmailService).sendWelcomeEmail(any(), any(), any());
        when(adminProvisioningService.createDefaultAdmin(any(), any(), any(), any()))
                .thenReturn("temp-pass-123");

        var response = tenantService.createTenant(acmeRequest());

        // A transient email failure must NOT unwind an otherwise-successful
        // provisioning — schema, migrations, and admin user already exist.
        verify(schemaProvisioningService, never()).dropSchemaIfExists(anyString());
        assertThat(response.getStatus()).isEqualTo(TenantStatus.ACTIVE);
    }
}
