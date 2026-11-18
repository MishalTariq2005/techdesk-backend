package com.example.auth_service.rbac.service;

import com.example.auth_service.entity.User;
import com.example.auth_service.repository.UserRepository;
import com.example.auth_service.rbac.dto.*;
import com.example.auth_service.rbac.entity.UserInvitation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class UserManagementService {

    private final InvitationService invitationService;
    private final PermissionResolutionService permissionResolutionService;
    private final UserRepository userRepository;

    public UserManagementService(InvitationService invitationService,
                                  PermissionResolutionService permissionResolutionService,
                                  UserRepository userRepository) {
        this.invitationService = invitationService;
        this.permissionResolutionService = permissionResolutionService;
        this.userRepository = userRepository;
    }

    public PagedUserResponse listUsers(String role, String department, String status,
                                        int page, int size) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();
        if (role != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("role"), role));
        }
        if (department != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("department"), department));
        }
        if (status != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        }

        Page<User> result = userRepository.findAll(spec, PageRequest.of(page, size));

        return new PagedUserResponse(
            result.getContent().stream().map(this::toResponse).toList(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    /**
     * NEW — single-user lookup by id. Added for asset-service's
     * UserLookupClient (5.1's double-assignment 409 needs a display name
     * for the current assignee). Reuses the same toResponse() mapping as
     * listUsers() — no new response shape needed.
     */
    public UserResponse getUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return toResponse(user);
    }

    public UserPermissionsResponse getUserPermissions(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Set<String> perms = Set.copyOf(user.getPermissionsList());
        return new UserPermissionsResponse(userId, user.getRole(), perms);
    }

    @Transactional
    public void inviteUser(InviteUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("A user with this email already exists in this tenant");
        }
        invitationService.inviteUser(request);
    }

    @Transactional
    public void acceptInvite(AcceptInviteRequest request) {
        UserInvitation invitation = invitationService.validateAndConsumeToken(request.token());
        String passwordHash = invitationService.hashPassword(request.password());
        String permissionsCsv = permissionResolutionService.getEffectivePermissionsAsCsv(invitation.getRole());

        User user = User.fromAcceptedInvite(
            invitation.getEmail(),
            passwordHash,
            request.firstName(),
            request.lastName(),
            invitation.getRole(),
            invitation.getDepartment(),
            permissionsCsv
        );

        userRepository.save(user);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFirstName(),
            user.getLastName(), user.getRole(), user.getDepartment(), user.getStatus(),
            user.getCreatedAt());
    }
}