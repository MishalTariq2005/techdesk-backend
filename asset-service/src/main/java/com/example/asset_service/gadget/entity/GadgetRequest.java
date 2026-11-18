package com.example.asset_service.gadget.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gadget_requests")
public class GadgetRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "gadget_type_id", nullable = false)
    private UUID gadgetTypeId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;   // requester — no FK, users live in auth-service's schema

    @Column(length = 1000)
    private String reason;

    @Column(nullable = false, length = 30)
    private String status = "REQUESTED";

    @Column(name = "assigned_asset_id")
    private UUID assignedAssetId;   // populated only once status reaches ASSIGNED

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // Same concurrent-write protection as Asset.version — two approve
    // attempts (or an approve racing a cancel) on the same request are
    // caught here rather than silently letting the second write clobber
    // the first with no error.
    @Version
    private Long version;

    protected GadgetRequest() {}

    public GadgetRequest(UUID gadgetTypeId, UUID employeeId, String reason) {
        this.gadgetTypeId = gadgetTypeId;
        this.employeeId = employeeId;
        this.reason = reason;
    }

    public UUID getId() { return id; }
    public UUID getGadgetTypeId() { return gadgetTypeId; }
    public UUID getEmployeeId() { return employeeId; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; this.updatedAt = Instant.now(); }
    public UUID getAssignedAssetId() { return assignedAssetId; }
    public void setAssignedAssetId(UUID assignedAssetId) { this.assignedAssetId = assignedAssetId; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
