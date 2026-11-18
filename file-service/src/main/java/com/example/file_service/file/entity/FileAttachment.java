package com.example.file_service.file.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * parentType + parentId is a polymorphic reference — deliberately not a FK
 * to either tickets or gadget_requests, since neither lives in this
 * service's schema (tickets are ticket-service's, gadget_requests are
 * asset-service's, despite both physically sitting in the same Postgres
 * database under the same tenant schema — this service has no JPA mapping
 * for either, so there's nothing to FK against even though the row
 * technically exists nearby).
 *
 * storagePath is a relative path under app.file-storage.root, namespaced by
 * tenant schema (see FileStorageService) — not stored as an absolute path,
 * so the storage root can move without invalidating every row.
 */
@Entity
@Table(name = "file_attachments")
public class FileAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "parent_type", nullable = false, length = 20)
    private FileParentType parentType;

    @Column(name = "parent_id", nullable = false)
    private UUID parentId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_path", nullable = false, unique = true)
    private String storagePath;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected FileAttachment() {}

    public FileAttachment(FileParentType parentType, UUID parentId, String originalFilename,
                          String contentType, long sizeBytes, String storagePath, UUID uploadedBy) {
        this.parentType = parentType;
        this.parentId = parentId;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storagePath = storagePath;
        this.uploadedBy = uploadedBy;
    }

    public UUID getId() { return id; }
    public FileParentType getParentType() { return parentType; }
    public UUID getParentId() { return parentId; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getStoragePath() { return storagePath; }
    public UUID getUploadedBy() { return uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
