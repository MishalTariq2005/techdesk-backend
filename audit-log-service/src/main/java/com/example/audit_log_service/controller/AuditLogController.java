package com.example.audit_log_service.controller;

import com.example.audit_log_service.dto.AuditEventRequest;
import com.example.audit_log_service.dto.AuditLogResponse;
import com.example.audit_log_service.dto.VerifyResult;
import com.example.audit_log_service.entity.AuditLogEntry;
import com.example.audit_log_service.exception.InsufficientPermissionException;
import com.example.audit_log_service.security.CurrentUserContext;
import com.example.audit_log_service.service.AuditIngestService;
import com.example.audit_log_service.service.AuditQueryService;
import com.example.audit_log_service.service.TenantResolutionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private static final String AUDIT_VIEW = "AUDIT_VIEW";

    private final AuditIngestService ingestService;
    private final AuditQueryService queryService;
    private final TenantResolutionService tenantResolutionService;

    public AuditLogController(AuditIngestService ingestService, AuditQueryService queryService,
                               TenantResolutionService tenantResolutionService) {
        this.ingestService = ingestService;
        this.queryService = queryService;
        this.tenantResolutionService = tenantResolutionService;
    }

    /**
     * Write endpoint. Any authenticated caller (i.e. any service forwarding
     * a real user's JWT — same forwarding pattern as NotificationClient) can
     * record an audit event about the action they're already authorized to
     * perform. No AUDIT_VIEW check here — writing an audit trail of your own
     * action isn't the same privilege as reading everyone else's.
     */
    @PostMapping("/events")
    public ResponseEntity<AuditLogResponse> recordEvent(@Valid @RequestBody AuditEventRequest request,
                                                          HttpServletRequest httpRequest) {
        AuditLogEntry entry = ingestService.record(request, clientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.status(HttpStatus.CREATED).body(AuditLogResponse.from(entry));
    }

    @GetMapping
    public ResponseEntity<List<AuditLogResponse>> list(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Long tenantId = requireAuditView();
        Page<AuditLogEntry> results = queryService.search(
                tenantId, entityType, actorId, from, to, PageRequest.of(page, size));
        return ResponseEntity.ok(results.map(AuditLogResponse::from).getContent());
    }

    @GetMapping("/verify")
    public ResponseEntity<VerifyResult> verify(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        Long tenantId = requireAuditView();
        return ResponseEntity.ok(queryService.verify(tenantId, from, to));
    }

    /** Shared by the two read endpoints: enforce AUDIT_VIEW, resolve caller's own tenant id. */
    private Long requireAuditView() {
        CurrentUserContext.Identity identity = CurrentUserContext.get();
        if (identity == null) {
            throw new InsufficientPermissionException("No authenticated identity on this request");
        }
        if (!identity.permissions().contains(AUDIT_VIEW)) {
            throw new InsufficientPermissionException(
                    "User " + identity.userId() + " (role=" + identity.role() + ") lacks required permission AUDIT_VIEW");
        }
        Long tenantId = tenantResolutionService.resolveTenantId(identity.tenantId());
        if (tenantId == null) {
            throw new InsufficientPermissionException("Unable to resolve caller's tenant");
        }
        return tenantId;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
