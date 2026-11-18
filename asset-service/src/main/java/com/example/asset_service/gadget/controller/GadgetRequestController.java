package com.example.asset_service.gadget.controller;

import com.example.asset_service.gadget.dto.*;
import com.example.asset_service.gadget.service.GadgetRequestService;
import com.example.asset_service.rbac.annotation.RequiresPermission;
import com.example.asset_service.rbac.security.CurrentUserContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Creation and self-service (list-mine, get, cancel) are open to any
 * authenticated user — same reasoning as ticket creation having no
 * @RequiresPermission at all (no GADGET_CREATE permission exists in the V2
 * seed; EMPLOYEE's role description just says "raises tickets and gadget
 * requests" as free text). Approval-path actions (approve/reject/
 * procurement-complete) are gated by GADGET_APPROVE, which already existed
 * pre-5.2, granted to COMPANY_ADMIN and IT_MANAGER.
 */
@RestController
@RequestMapping("/api/gadget-requests")
public class GadgetRequestController {

    private final GadgetRequestService service;

    public GadgetRequestController(GadgetRequestService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission("GADGET_APPROVE")
    public ResponseEntity<PagedGadgetRequestResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Gated: this is the "all requests across the tenant" view, the one
        // an IT Manager's approval queue needs. Employees see their own via
        // the /mine endpoint below instead, without needing GADGET_APPROVE.
        return ResponseEntity.ok(service.list(status, employeeId, page, size));
    }

    @GetMapping("/mine")
    public ResponseEntity<PagedGadgetRequestResponse> listMine(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID callerId = UUID.fromString(CurrentUserContext.get().userId());
        return ResponseEntity.ok(service.list(status, callerId, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GadgetRequestResponse> getById(@PathVariable UUID id) {
        // Deliberately ungated at the permission-annotation level: an
        // employee needs to view their own request's status. A stricter
        // version would check requester-or-manager here; left open for now,
        // consistent with this service's overall default of gating writes
        // over reads (see AssetController's own note on this tradeoff).
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/{id}/audit-replay")
    @RequiresPermission("GADGET_APPROVE")
    public ResponseEntity<GadgetRequestDetailResponse> getAuditReplay(@PathVariable UUID id) {
        // The concrete artifact for 5.2's audit-replay challenge — full
        // reconstructable timeline in one response. Gated by GADGET_APPROVE:
        // this is a review/oversight action, not something the requesting
        // employee needs day-to-day.
        return ResponseEntity.ok(service.getAuditReplay(id));
    }

    @PostMapping
    public ResponseEntity<GadgetRequestResponse> create(@Valid @RequestBody CreateGadgetRequestRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PatchMapping("/{id}/approve")
    @RequiresPermission("GADGET_APPROVE")
    public ResponseEntity<GadgetRequestResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(service.approve(id));
    }

    @PatchMapping("/{id}/reject")
    @RequiresPermission("GADGET_APPROVE")
    public ResponseEntity<GadgetRequestResponse> reject(@PathVariable UUID id,
                                                         @Valid @RequestBody RejectGadgetRequestRequest request) {
        return ResponseEntity.ok(service.reject(id, request));
    }

    @PatchMapping("/{id}/procurement-complete")
    @RequiresPermission("GADGET_APPROVE")
    public ResponseEntity<GadgetRequestResponse> retryProcurement(@PathVariable UUID id) {
        return ResponseEntity.ok(service.retryProcurement(id));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<GadgetRequestResponse> cancel(@PathVariable UUID id) {
        // Ungated at the annotation level; GadgetRequestService itself
        // enforces "only the original requester" — an authorization check
        // that depends on request DATA (who filed it), not a static
        // permission, so it belongs in the service, not @RequiresPermission.
        return ResponseEntity.ok(service.cancel(id));
    }
}
