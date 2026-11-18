package com.example.asset_service.asset.controller;

import com.example.asset_service.asset.dto.*;
import com.example.asset_service.asset.entity.AssetStatus;
import com.example.asset_service.asset.service.AssetService;
import com.example.asset_service.rbac.annotation.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Every endpoint gated by a single ASSET_MANAGE permission for now — the
 * roadmap doesn't specify a read/write split for assets the way ticket
 * management ended up with two different precedents (TicketCategoryController
 * leaves GET open to any authenticated caller; StaffProfileController gates
 * everything under TICKET_ASSIGN). Assigned-employee data is arguably
 * sensitive enough that gating reads too is the safer default. Revisit if
 * the employee portal (5.5) ends up needing a narrower "see my own assigned
 * asset(s)" read path — that's a different, additive concern, not in scope
 * here. ASSET_MANAGE is granted to COMPANY_ADMIN and IT_MANAGER in the V10
 * migration, same roles ticket-service's equivalent permissions target.
 */
@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    @RequiresPermission("ASSET_MANAGE")
    public ResponseEntity<PagedAssetResponse> list(
            @RequestParam(required = false) AssetStatus status,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(assetService.list(status, assetType, assignedTo, page, size));
    }

    @GetMapping("/{id}")
    @RequiresPermission("ASSET_MANAGE")
    public ResponseEntity<AssetResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(assetService.getById(id));
    }

    @PostMapping
    @RequiresPermission("ASSET_MANAGE")
    public ResponseEntity<AssetResponse> create(@Valid @RequestBody AssetCreateRequest request) {
        return ResponseEntity.ok(assetService.create(request));
    }

    @PatchMapping("/{id}")
    @RequiresPermission("ASSET_MANAGE")
    public ResponseEntity<AssetResponse> update(@PathVariable UUID id,
                                                 @Valid @RequestBody AssetUpdateRequest request) {
        return ResponseEntity.ok(assetService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("ASSET_MANAGE")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        assetService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/assign")
    @RequiresPermission("ASSET_MANAGE")
    public ResponseEntity<AssetResponse> assign(@PathVariable UUID id,
                                                 @Valid @RequestBody AssetAssignRequest request) {
        return ResponseEntity.ok(assetService.assign(id, request));
    }

    @PatchMapping("/{id}/return")
    @RequiresPermission("ASSET_MANAGE")
    public ResponseEntity<AssetResponse> unassign(@PathVariable UUID id) {
        return ResponseEntity.ok(assetService.unassign(id));
    }
}
