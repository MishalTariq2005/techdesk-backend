package com.example.reporting_service.reportjob.service;

import com.example.reporting_service.multitenancy.TenantContext;
import com.example.reporting_service.reportjob.entity.ReportJob;
import com.example.reporting_service.reportjob.repository.ReportJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Every method here takes tenantSchema explicitly and sets/clears
 * TenantContext around its own work, rather than assuming ambient context
 * is already correct. That's deliberate: markComplete()/markFailed() are
 * called from a CompletableFuture completion callback (AsyncReportExecutor),
 * which may run on a DIFFERENT thread than the one that started the report
 * — a thread with no TenantContext of its own at all. Request-thread callers
 * (create()) already have TenantContext set by TenantIdentifierFilter, but
 * setting it again here is harmless and keeps this service's contract
 * uniform regardless of caller.
 */
@Service
public class ReportJobService {

    private static final Logger log = LoggerFactory.getLogger(ReportJobService.class);

    private final ReportJobRepository repository;
    private final ObjectMapper objectMapper;

    public ReportJobService(ReportJobRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public UUID createPending(String tenantSchema, String reportType, UUID requestedBy, Object params) {
        return withTenant(tenantSchema, () -> {
            String paramsJson = toJsonOrNull(params);
            ReportJob job = new ReportJob(reportType, requestedBy, paramsJson);
            job = repository.save(job);
            return job.getId();
        });
    }

    public void markComplete(String tenantSchema, UUID jobId, Object result) {
        withTenant(tenantSchema, () -> {
            repository.findById(jobId).ifPresentOrElse(job -> {
                job.setStatus(ReportJob.READY);
                job.setResult(toJsonOrNull(result));
                job.setCompletedAt(Instant.now());
                repository.save(job);
            }, () -> log.warn("Report job {} not found when marking complete (tenant {})", jobId, tenantSchema));
            return null;
        });
    }

    public void markFailed(String tenantSchema, UUID jobId, String errorMessage) {
        withTenant(tenantSchema, () -> {
            repository.findById(jobId).ifPresentOrElse(job -> {
                job.setStatus(ReportJob.FAILED);
                job.setErrorMessage(errorMessage);
                job.setCompletedAt(Instant.now());
                repository.save(job);
            }, () -> log.warn("Report job {} not found when marking failed (tenant {})", jobId, tenantSchema));
            return null;
        });
    }

    public ReportJob get(UUID jobId) {
        return repository.findById(jobId).orElse(null);
    }

    private <T> T withTenant(String tenantSchema, java.util.function.Supplier<T> work) {
        TenantContext.setCurrentTenant(tenantSchema);
        try {
            return work.get();
        } finally {
            TenantContext.clear();
        }
    }

    private String toJsonOrNull(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize report job payload to JSON", e);
            return null;
        }
    }
}
