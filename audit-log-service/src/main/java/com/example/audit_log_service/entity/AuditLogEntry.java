package com.example.audit_log_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps to the shared public.audit_logs table (see V1 migration). Immutable
 * by convention AND by DB trigger — this entity is never updated after
 * insert, only read back for GET /api/audit-logs and re-derived for
 * signature verification.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID actorId;

    private Long tenantId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(length = 100)
    private String entityType;

    @Column(length = 100)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String newValue;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 255)
    private String userAgent;

    @Column(length = 88)
    private String signature;

    private Instant createdAt;

    protected AuditLogEntry() {
    }

    public AuditLogEntry(UUID actorId, Long tenantId, String action, String entityType,
                          String entityId, String oldValue, String newValue,
                          String ipAddress, String userAgent) {
        this.actorId = actorId;
        this.tenantId = tenantId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getActorId() {
        return actorId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Canonical string this row's HMAC is computed over. Field order and
     * null-handling here MUST exactly match HmacSigningService's usage on
     * both the write path (AuditIngestService) and the verify path
     * (AuditQueryService) — any drift between the two would make every
     * historical signature look tampered even though nothing changed.
     */
    public String canonicalPayload() {
        return String.join("|",
                nullSafe(actorId), nullSafe(tenantId), nullSafe(action),
                nullSafe(entityType), nullSafe(entityId),
                nullSafe(oldValue), nullSafe(newValue),
                nullSafe(ipAddress), nullSafe(userAgent),
                createdAt == null ? "" : createdAt.toString());
    }

    private String nullSafe(Object value) {
        return value == null ? "" : value.toString();
    }
}
