package com.example.ticket_service.ticket.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String status = "OPEN";

    @Column(nullable = false)
    private String priority;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "reporter_department")
    private String reporterDepartment;

    @Column(name = "assigned_to")
    private UUID assignedTo; // null until 4.4's auto-assignment engine exists

    @Column(name = "sla_due_at")
    private Instant slaDueAt;

    @Column(name = "sla_breached", nullable = false)
    private boolean slaBreached = false;

    @Column(name = "sla_warning_sent", nullable = false)
    private boolean slaWarningSent = false;

    /**
     * The optimistic-locking challenge (4.3 ⚡): Hibernate checks this column
     * on every UPDATE (WHERE id = ? AND version = ?). If a concurrent
     * transaction already bumped it, this UPDATE affects 0 rows and Hibernate
     * throws OptimisticLockException — TicketService catches that and
     * surfaces it as a 409. No manual version-comparison code needed; this
     * annotation is the entire mechanism.
     */
    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    protected Ticket() {}

    public Ticket(UUID categoryId, String title, String description, String priority,
                  UUID createdBy, String reporterDepartment) {
        this.categoryId = categoryId;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.createdBy = createdBy;
        this.reporterDepartment = reporterDepartment;
    }

    public UUID getId() { return id; }
    public UUID getCategoryId() { return categoryId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; this.updatedAt = Instant.now(); }
    public String getPriority() { return priority; }
    public UUID getCreatedBy() { return createdBy; }
    public String getReporterDepartment() { return reporterDepartment; }
    public UUID getAssignedTo() { return assignedTo; }
    public void setAssignedTo(UUID assignedTo) { this.assignedTo = assignedTo; this.updatedAt = Instant.now(); }
    public Instant getSlaDueAt() { return slaDueAt; }
    public void setSlaDueAt(Instant slaDueAt) { this.slaDueAt = slaDueAt; }
    public boolean isSlaBreached() { return slaBreached; }
    public void setSlaBreached(boolean slaBreached) { this.slaBreached = slaBreached; }
    public boolean isSlaWarningSent() { return slaWarningSent; }
    public void setSlaWarningSent(boolean slaWarningSent) { this.slaWarningSent = slaWarningSent; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
