package com.example.audit_log_service.dto;

import com.example.audit_log_service.entity.AuditLogEntry;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        Long id,
        UUID actorId,
        Long tenantId,
        String action,
        String entityType,
        String entityId,
        String oldValue,
        String newValue,
        String ipAddress,
        String userAgent,
        Instant createdAt
) {
    public static AuditLogResponse from(AuditLogEntry entry) {
        return new AuditLogResponse(
                entry.getId(), entry.getActorId(), entry.getTenantId(), entry.getAction(),
                entry.getEntityType(), entry.getEntityId(), entry.getOldValue(), entry.getNewValue(),
                entry.getIpAddress(), entry.getUserAgent(), entry.getCreatedAt());
    }
}
