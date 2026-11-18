package com.example.asset_service.asset.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "assets")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "serial_number", nullable = false, unique = true)
    private String serialNumber;

    @Column(nullable = false)
    private String name;

    @Column(name = "asset_type")
    private String assetType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetStatus status = AssetStatus.AVAILABLE;

    // No FK — the employee/user lives in auth-service's data, not this
    // service's, even though it's the same physical schema (same reasoning
    // as ticket-service's StaffProfile.staffUserId).
    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "warranty_expiry_date")
    private LocalDate warrantyExpiryDate;

    private String notes;

    // Soft delete marker — NULL means active. Chosen over an is_deleted
    // boolean so we also capture *when* it was removed, for free.
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // Defends against a real race the status check alone can't catch: two
    // concurrent /assign requests can both read status=AVAILABLE and both
    // pass that check before either commits — without this, the second
    // write would silently overwrite the first with no error at all. Same
    // mechanism as Ticket.version in ticket-service (4.3's optimistic-
    // locking challenge), applied here for the same reason.
    @Version
    private Long version;

    protected Asset() {}

    public Asset(String serialNumber, String name, String assetType,
                 LocalDate purchaseDate, LocalDate warrantyExpiryDate, String notes) {
        this.serialNumber = serialNumber;
        this.name = name;
        this.assetType = assetType;
        this.purchaseDate = purchaseDate;
        this.warrantyExpiryDate = warrantyExpiryDate;
        this.notes = notes;
    }

    public UUID getId() { return id; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }
    public AssetStatus getStatus() { return status; }
    public void setStatus(AssetStatus status) { this.status = status; }
    public UUID getAssignedTo() { return assignedTo; }
    public void setAssignedTo(UUID assignedTo) { this.assignedTo = assignedTo; }
    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public LocalDate getWarrantyExpiryDate() { return warrantyExpiryDate; }
    public void setWarrantyExpiryDate(LocalDate warrantyExpiryDate) { this.warrantyExpiryDate = warrantyExpiryDate; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
    public void touch() { this.updatedAt = Instant.now(); }
}
