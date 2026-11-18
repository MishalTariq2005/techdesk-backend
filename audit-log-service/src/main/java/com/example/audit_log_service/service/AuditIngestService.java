package com.example.audit_log_service.service;

import com.example.audit_log_service.dto.AuditEventRequest;
import com.example.audit_log_service.entity.AuditLogEntry;
import com.example.audit_log_service.repository.AuditLogEntryRepository;
import com.example.audit_log_service.security.CurrentUserContext;
import com.example.audit_log_service.security.HmacSigningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Write path for audit events. Mirrors notification-service's role as the
 * call-TARGET in a fire-and-forget relationship (5.4's architecture
 * decision) — other services POST here, this never calls back out to them.
 * Deliberately does NOT wrap this in a broad try/catch the way
 * NotificationClient's callers do on their end: whether a given audit
 * event's *write* succeeds is this service's own problem, but each CALLER
 * (once 6.3's client wiring lands service-by-service) is expected to treat
 * audit dispatch the same fire-and-forget way notifications already are —
 * an audit-log-service outage must never block the write operation being
 * audited.
 */
@Service
public class AuditIngestService {

    private final AuditLogEntryRepository repository;
    private final HmacSigningService hmacSigningService;
    private final TenantResolutionService tenantResolutionService;
    private final ObjectMapper objectMapper;

    public AuditIngestService(AuditLogEntryRepository repository,
                               HmacSigningService hmacSigningService,
                               TenantResolutionService tenantResolutionService,
                               ObjectMapper objectMapper) {
        this.repository = repository;
        this.hmacSigningService = hmacSigningService;
        this.tenantResolutionService = tenantResolutionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuditLogEntry record(AuditEventRequest request, String ipAddress, String userAgent) {
        CurrentUserContext.Identity caller = CurrentUserContext.get();
        UUID actorId = (caller != null && caller.userId() != null)
                ? safeUuid(caller.userId())
                : null;
        Long tenantId = (caller != null)
                ? tenantResolutionService.resolveTenantId(caller.tenantId())
                : null;

        // FIX: old_value/new_value are jsonb columns in Postgres, which
        // require the STORED value itself to be valid JSON — a bare word
        // like OPEN isn't valid JSON, but the JSON string "OPEN" is. The
        // request DTO accepts a plain String (callers shouldn't have to
        // pre-encode JSON themselves just to log a simple before/after
        // value), so encode it here at the boundary instead. writeValueAsString
        // on a String produces a properly quoted/escaped JSON string literal.
        String oldValueJson = toJsonOrNull(request.oldValue());
        String newValueJson = toJsonOrNull(request.newValue());

        AuditLogEntry entry = new AuditLogEntry(
                actorId, tenantId, request.action(), request.entityType(), request.entityId(),
                oldValueJson, newValueJson, ipAddress, userAgent);
        // Sign AFTER createdAt is set (constructor sets it) but BEFORE
        // persist, since canonicalPayload() includes createdAt and the
        // signature must cover the row exactly as it will be stored.
        entry.setSignature(hmacSigningService.sign(entry.canonicalPayload()));
        return repository.save(entry);
    }

    private String toJsonOrNull(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            // Fall back to a JSON string of the raw toString() rather than
            // failing the whole audit write over an encoding hiccup.
            return objectMapper.valueToTree(String.valueOf(value)).toString();
        }
    }

    private UUID safeUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}