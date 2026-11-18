package com.example.audit_log_service.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * actorId and tenantId are deliberately NOT fields here — they're derived
 * server-side from the caller's own JWT (see AuditIngestService), same
 * reasoning as why file-service never trusts a client-supplied tenant on
 * anything security-relevant. A calling service could otherwise attribute
 * an audit event to a different user or tenant than the one it actually
 * authenticated as.
 */
public record AuditEventRequest(
        @NotBlank String action,
        String entityType,
        String entityId,
        String oldValue,
        String newValue
) {
}
