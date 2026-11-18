package com.example.reporting_service.report.controller;

import com.example.reporting_service.async.AsyncReportExecutor;
import com.example.reporting_service.async.AsyncReportExecutor.Outcome;
import com.example.reporting_service.multitenancy.TenantContext;
import com.example.reporting_service.rbac.annotation.RequiresPermission;
import com.example.reporting_service.rbac.exception.InsufficientPermissionException;
import com.example.reporting_service.rbac.security.CurrentUserContext;
import com.example.reporting_service.report.dto.ReportDtos.*;
import com.example.reporting_service.report.service.ReportQueryService;
import com.example.reporting_service.reportjob.entity.ReportJob;
import com.example.reporting_service.reportjob.service.ReportJobService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final AsyncReportExecutor asyncExecutor;
    private final ReportJobService jobService;
    private final ReportQueryService queryService;

    public ReportController(AsyncReportExecutor asyncExecutor, ReportJobService jobService,
                             ReportQueryService queryService) {
        this.asyncExecutor = asyncExecutor;
        this.jobService = jobService;
        this.queryService = queryService;
    }

    @GetMapping("/ticket-summary")
    @RequiresPermission("REPORT_VIEW")
    public ResponseEntity<?> ticketSummary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        String tenantSchema = requireTenant();
        Instant f = defaultFrom(from);
        Instant t = defaultTo(to);
        return dispatch(tenantSchema, "ticket-summary", Map.of("from", f, "to", t),
                () -> queryService.ticketSummary(tenantSchema, f, t));
    }

    @GetMapping("/staff-performance")
    @RequiresPermission("REPORT_VIEW")
    public ResponseEntity<?> staffPerformance(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        String tenantSchema = requireTenant();
        Instant f = defaultFrom(from);
        Instant t = defaultTo(to);
        return dispatch(tenantSchema, "staff-performance", Map.of("from", f, "to", t),
                () -> queryService.staffPerformance(tenantSchema, f, t));
    }

    @GetMapping("/category-breakdown")
    @RequiresPermission("REPORT_VIEW")
    public ResponseEntity<?> categoryBreakdown(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        String tenantSchema = requireTenant();
        Instant f = defaultFrom(from);
        Instant t = defaultTo(to);
        return dispatch(tenantSchema, "category-breakdown", Map.of("from", f, "to", t),
                () -> queryService.categoryBreakdown(tenantSchema, f, t));
    }

    @GetMapping("/asset-utilization")
    @RequiresPermission("REPORT_VIEW")
    public ResponseEntity<?> assetUtilization() {
        String tenantSchema = requireTenant();
        return dispatch(tenantSchema, "asset-utilization", Map.of(),
                () -> queryService.assetUtilization(tenantSchema));
    }

    @GetMapping("/sla-breaches")
    @RequiresPermission("REPORT_VIEW")
    public ResponseEntity<?> slaBreaches(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        String tenantSchema = requireTenant();
        Instant f = defaultFrom(from);
        Instant t = defaultTo(to);
        return dispatch(tenantSchema, "sla-breaches", Map.of("from", f, "to", t),
                () -> queryService.slaBreaches(tenantSchema, f, t));
    }

    @GetMapping("/jobs/{jobId}/status")
    @RequiresPermission("REPORT_VIEW")
    public ResponseEntity<Map<String, Object>> jobStatus(@PathVariable UUID jobId) {
        ReportJob job = jobService.get(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("jobId", job.getId(), "status", job.getStatus(),
                "reportType", job.getReportType(), "createdAt", job.getCreatedAt()));
    }

    @GetMapping("/jobs/{jobId}/result")
    @RequiresPermission("REPORT_VIEW")
    public ResponseEntity<?> jobResult(@PathVariable UUID jobId) {
        ReportJob job = jobService.get(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        if (ReportJob.PENDING.equals(job.getStatus())) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of("jobId", job.getId(), "status", job.getStatus()));
        }
        if (ReportJob.FAILED.equals(job.getStatus())) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("jobId", job.getId(), "status", job.getStatus(), "error", job.getErrorMessage()));
        }
        // READY — result is stored as a JSON string; return it as raw JSON,
        // not double-encoded as a string field.
        return ResponseEntity.ok().header("Content-Type", "application/json").body(job.getResult());
    }

    /**
     * The 6.2 challenge's core contract: run the query; if it beats the
     * sync threshold, return 200 with the result directly; otherwise create
     * a report_jobs row, attach a completion handler that persists the
     * eventual result, and return 202 with the jobId to poll.
     */
    private ResponseEntity<?> dispatch(String tenantSchema, String reportType, Map<String, Object> params,
                                        java.util.function.Supplier<Object> work) {
        UUID requestedBy = requireCallerId();

        Outcome<Object> outcome = asyncExecutor.run(work);

        if (outcome instanceof Outcome.Immediate<Object> immediate) {
            return ResponseEntity.ok(immediate.result());
        }

        Outcome.Pending<Object> pending = (Outcome.Pending<Object>) outcome;
        UUID jobId = jobService.createPending(tenantSchema, reportType, requestedBy, params);

        pending.future().whenComplete((result, ex) -> {
            if (ex != null) {
                jobService.markFailed(tenantSchema, jobId, ex.getMessage());
            } else {
                jobService.markComplete(tenantSchema, jobId, result);
            }
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("jobId", jobId, "status", ReportJob.PENDING,
                        "statusUrl", "/api/reports/jobs/" + jobId + "/status",
                        "resultUrl", "/api/reports/jobs/" + jobId + "/result"));
    }

    private Instant defaultFrom(Instant from) {
        return from != null ? from : Instant.now().minus(30, ChronoUnit.DAYS);
    }

    private Instant defaultTo(Instant to) {
        return to != null ? to : Instant.now();
    }

    private String requireTenant() {
        String tenant = TenantContext.getCurrentTenant();
        if (tenant == null) {
            throw new IllegalStateException("No tenant resolved for this request");
        }
        return tenant;
    }

    private UUID requireCallerId() {
        CurrentUserContext.Identity identity = CurrentUserContext.get();
        if (identity == null || identity.userId() == null) {
            throw new InsufficientPermissionException("No authenticated identity on this request");
        }
        try {
            return UUID.fromString(identity.userId());
        } catch (IllegalArgumentException e) {
            throw new InsufficientPermissionException("Caller identity is not a valid user id");
        }
    }
}
