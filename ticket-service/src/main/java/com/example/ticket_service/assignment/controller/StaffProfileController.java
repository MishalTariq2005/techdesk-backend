package com.example.ticket_service.assignment.controller;

import com.example.ticket_service.assignment.dto.*;
import com.example.ticket_service.assignment.service.StaffProfileService;
import com.example.ticket_service.rbac.annotation.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * TICKET_ASSIGN-gated (IT_MANAGER and COMPANY_ADMIN both have it per the
 * V2 permission seed) — setting up who's available and what they're skilled
 * at is an assignment-management concern, same permission the roadmap
 * already associates with staff workload/reassignment in 4.1's seed data.
 */
@RestController
@RequestMapping("/api/staff-profiles")
public class StaffProfileController {

    private final StaffProfileService service;

    public StaffProfileController(StaffProfileService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission("TICKET_ASSIGN")
    public ResponseEntity<List<StaffProfileResponse>> listAll() {
        return ResponseEntity.ok(service.listAll());
    }

    @GetMapping("/{staffUserId}")
    @RequiresPermission("TICKET_ASSIGN")
    public ResponseEntity<StaffProfileResponse> get(@PathVariable UUID staffUserId) {
        return ResponseEntity.ok(service.getProfile(staffUserId));
    }

    @PostMapping
    @RequiresPermission("TICKET_ASSIGN")
    public ResponseEntity<StaffProfileResponse> upsert(@Valid @RequestBody UpsertStaffProfileRequest request) {
        return ResponseEntity.ok(service.upsertProfile(request));
    }

    @GetMapping("/{staffUserId}/skills")
    @RequiresPermission("TICKET_ASSIGN")
    public ResponseEntity<List<StaffSkillResponse>> listSkills(@PathVariable UUID staffUserId) {
        return ResponseEntity.ok(service.listSkills(staffUserId));
    }

    @PostMapping("/{staffUserId}/skills")
    @RequiresPermission("TICKET_ASSIGN")
    public ResponseEntity<StaffSkillResponse> upsertSkill(@PathVariable UUID staffUserId,
                                                            @Valid @RequestBody UpsertStaffSkillRequest request) {
        return ResponseEntity.ok(service.upsertSkill(staffUserId, request));
    }
}
