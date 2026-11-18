package com.example.auth_service.rbac.controller;
import com.example.auth_service.rbac.security.CurrentUserContext;

import com.example.auth_service.rbac.annotation.RequiresPermission;
import com.example.auth_service.rbac.dto.*;
import com.example.auth_service.rbac.service.UserManagementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserManagementService userManagementService;

    public UserController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    @RequiresPermission("USER_VIEW")
    public ResponseEntity<PagedUserResponse> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userManagementService.listUsers(role, department, status, page, size));
    }
    /**
     * NEW — self-view. Unlike every other method on this controller, this
     * one has NO @RequiresPermission at all: viewing your OWN basic account
     * info shouldn't need an elevated permission, only a valid JWT (which
     * the filter chain already guarantees before this method is reached).
     * Fixes a real gap found during Week 4 testing — EMPLOYEE and IT_STAFF
     * previously had no way to see their own name/email/department through
     * this API at all (their JWT only carries sub/role/tenantId/permissions,
     * no human-readable fields).
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyProfile() {
        CurrentUserContext.Identity caller = CurrentUserContext.get();
        UUID callerId = UUID.fromString(caller.userId());
        return ResponseEntity.ok(userManagementService.getUser(callerId));
    }
    @GetMapping("/{id}")
    @RequiresPermission("USER_VIEW")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userManagementService.getUser(id));
    }

    @GetMapping("/{id}/permissions")
    @RequiresPermission({"USER_VIEW", "USER_MANAGE"})
    public ResponseEntity<UserPermissionsResponse> getUserPermissions(@PathVariable UUID id) {
        return ResponseEntity.ok(userManagementService.getUserPermissions(id));
    }

    @PostMapping("/invite")
    @RequiresPermission("USER_INVITE")
    public ResponseEntity<Void> inviteUser(@Valid @RequestBody InviteUserRequest request) {
        userManagementService.inviteUser(request);
        return ResponseEntity.accepted().build();
    }

    /**
     * Deliberately NOT gated by @RequiresPermission — the caller isn't a user
     * yet. Must be added to SecurityConfig's permitAll list (same treatment
     * as /api/auth/**) and MUST NOT be routed through CurrentUserContextFilter's
     * JWT requirement, since there is no JWT at this point.
     */
    @PostMapping("/accept-invite")
    public ResponseEntity<Void> acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
        userManagementService.acceptInvite(request);
        return ResponseEntity.ok().build();
    }
}