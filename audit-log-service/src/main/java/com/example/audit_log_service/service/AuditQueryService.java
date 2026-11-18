package com.example.audit_log_service.service;

import com.example.audit_log_service.dto.VerifyResult;
import com.example.audit_log_service.entity.AuditLogEntry;
import com.example.audit_log_service.repository.AuditLogEntryRepository;
import com.example.audit_log_service.security.HmacSigningService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AuditQueryService {

    private final AuditLogEntryRepository repository;
    private final HmacSigningService hmacSigningService;

    public AuditQueryService(AuditLogEntryRepository repository, HmacSigningService hmacSigningService) {
        this.repository = repository;
        this.hmacSigningService = hmacSigningService;
    }

    /**
     * tenantId is always supplied by the controller from the CALLER's own
     * JWT, never from a query parameter — an IT_MANAGER or COMPANY_ADMIN can
     * only ever see their own tenant's audit trail, full stop, regardless of
     * what entity_type/actor_id/date filters they add on top.
     */
    public Page<AuditLogEntry> search(Long tenantId, String entityType, UUID actorId,
                                       Instant from, Instant to, Pageable pageable) {
        Specification<AuditLogEntry> spec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);

        if (entityType != null && !entityType.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityType"), entityType));
        }
        if (actorId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorId"), actorId));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }

        return repository.findAll(spec, pageable);
    }

    /**
     * The 6.3 challenge: recompute every entry's signature in [from, to] and
     * report any where the stored signature no longer matches — proof the
     * row was altered after being written (since UPDATE is blocked at the
     * DB level, a mismatch here can only mean a direct, out-of-band edit,
     * e.g. someone with raw superuser access bypassing the application
     * entirely — the audit-log equivalent of the RLS-vs-superuser gap
     * flagged since Week 1).
     */
    public VerifyResult verify(Long tenantId, Instant from, Instant to) {
        Specification<AuditLogEntry> spec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }

        List<AuditLogEntry> entries = repository.findAll(spec);
        List<Long> tampered = new ArrayList<>();
        for (AuditLogEntry entry : entries) {
            if (!hmacSigningService.verify(entry.canonicalPayload(), entry.getSignature())) {
                tampered.add(entry.getId());
            }
        }
        return new VerifyResult(entries.size(), tampered.size(), tampered);
    }
}
