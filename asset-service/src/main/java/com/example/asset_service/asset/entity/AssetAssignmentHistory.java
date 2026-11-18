package com.example.asset_service.asset.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "asset_assignment_history")
public class AssetAssignmentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private AssetAssignmentEventType eventType;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp = Instant.now();

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    private String notes;

    protected AssetAssignmentHistory() {}

    public AssetAssignmentHistory(UUID assetId, UUID employeeId, AssetAssignmentEventType eventType,
                                   UUID actorUserId, String notes) {
        this.assetId = assetId;
        this.employeeId = employeeId;
        this.eventType = eventType;
        this.actorUserId = actorUserId;
        this.notes = notes;
    }

    public UUID getId() { return id; }
    public UUID getAssetId() { return assetId; }
    public UUID getEmployeeId() { return employeeId; }
    public AssetAssignmentEventType getEventType() { return eventType; }
    public Instant getEventTimestamp() { return eventTimestamp; }
    public UUID getActorUserId() { return actorUserId; }
    public String getNotes() { return notes; }
}
