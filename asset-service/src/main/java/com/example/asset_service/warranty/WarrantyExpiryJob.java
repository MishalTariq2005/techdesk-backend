package com.example.asset_service.warranty;

import com.example.asset_service.asset.entity.Asset;
import com.example.asset_service.asset.repository.AssetRepository;
import com.example.asset_service.multitenancy.TenantContext;
import com.example.asset_service.multitenancy.TenantLookupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Genuinely new problem this project hasn't hit before now: every other
 * piece of tenant-scoped code runs inside an HTTP request, where
 * TenantIdentifierFilter has already set TenantContext for that one
 * request's thread before any repository call happens. A @Scheduled job
 * runs on its own thread with NO inbound request and therefore NO tenant
 * set — HibernateTenantIdentifierResolver would silently fall back to the
 * "public" schema (where `assets` doesn't even exist), and this job would
 * just quietly do nothing, for every real tenant, forever, with no error.
 *
 * So this loops over every ACTIVE tenant schema (TenantLookupService's new
 * listActiveTenantSchemas()) and sets/clears TenantContext manually around
 * each tenant's work — the same thing the filter does per-request, just
 * batch-style. One tenant's failure is caught and logged so it can't take
 * down the whole run for every other tenant.
 *
 * No Notification Service exists yet (that's 5.4) — same "SLF4J for now"
 * treatment as the priority-rule conflict logging in 4.2 and the
 * status-change logging flagged as a Week 4 (6.3) gap. Swap the log.info
 * call for a real notification dispatch once 5.4 exists.
 */
@Component
public class WarrantyExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(WarrantyExpiryJob.class);
    private static final int WARNING_WINDOW_DAYS = 30;

    private final AssetRepository assetRepository;
    private final TenantLookupService tenantLookupService;

    public WarrantyExpiryJob(AssetRepository assetRepository, TenantLookupService tenantLookupService) {
        this.assetRepository = assetRepository;
        this.tenantLookupService = tenantLookupService;
    }

    @Scheduled(cron = "0 0 1 * * *") // once daily, 1 AM server time
    public void checkExpiringWarranties() {
        List<String> tenantSchemas = tenantLookupService.listActiveTenantSchemas();
        for (String schema : tenantSchemas) {
            try {
                TenantContext.setCurrentTenant(schema);
                runForCurrentTenant(schema);
            } catch (Exception e) {
                // One tenant's bad data or a transient DB issue must not
                // skip every other tenant in the same run.
                log.error("Warranty expiry check failed for tenant {}", schema, e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    private void runForCurrentTenant(String schema) {
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(WARNING_WINDOW_DAYS);
        List<Asset> expiring = assetRepository.findByWarrantyExpiryDateBetweenAndDeletedAtIsNull(today, cutoff);
        for (Asset asset : expiring) {
            log.info("[{}] Warranty expiry alert: asset {} ({}, serial {}) expires {}",
                schema, asset.getId(), asset.getName(), asset.getSerialNumber(), asset.getWarrantyExpiryDate());
        }
    }
}
