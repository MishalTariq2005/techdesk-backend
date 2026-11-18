package com.example.reporting_service.reportjob.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "report_jobs")
public class ReportJob {

    public static final String PENDING = "PENDING";
    public static final String READY = "READY";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "report_type", nullable = false, length = 50)
    private String reportType;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String params;

    @Column(nullable = false, length = 20)
    private String status = PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String result;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ReportJob() {
    }

    public ReportJob(String reportType, UUID requestedBy, String params) {
        this.reportType = reportType;
        this.requestedBy = requestedBy;
        this.params = params;
    }

    public UUID getId() { return id; }
    public String getReportType() { return reportType; }
    public UUID getRequestedBy() { return requestedBy; }
    public String getParams() { return params; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
