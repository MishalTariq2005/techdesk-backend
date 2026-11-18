package com.example.asset_service.gadget.controller;

import com.example.asset_service.gadget.dto.GadgetTypeRequest;
import com.example.asset_service.gadget.dto.GadgetTypeResponse;
import com.example.asset_service.gadget.service.GadgetTypeService;
import com.example.asset_service.rbac.annotation.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * GADGET_TYPE_MANAGE is a NEW permission (see V11 migration) — distinct from
 * GADGET_APPROVE, which already existed since V2 for approving/rejecting a
 * specific request. Configuring the catalog itself (which types exist,
 * whether each needs approval) is COMPANY_ADMIN-only, same tier as
 * TICKET_CATEGORY_MANAGE.
 */
@RestController
@RequestMapping("/api/gadget-types")
public class GadgetTypeController {

    private final GadgetTypeService service;

    public GadgetTypeController(GadgetTypeService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<GadgetTypeResponse>> listAll() {
        // Deliberately NOT gated, same reasoning as TicketCategoryController's
        // listAll() — any employee filing a gadget request needs to see the
        // catalog to pick a type. Only mutation is admin-gated below.
        return ResponseEntity.ok(service.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<GadgetTypeResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    @RequiresPermission("GADGET_TYPE_MANAGE")
    public ResponseEntity<GadgetTypeResponse> create(@Valid @RequestBody GadgetTypeRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PatchMapping("/{id}")
    @RequiresPermission("GADGET_TYPE_MANAGE")
    public ResponseEntity<GadgetTypeResponse> update(@PathVariable UUID id, @Valid @RequestBody GadgetTypeRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("GADGET_TYPE_MANAGE")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
