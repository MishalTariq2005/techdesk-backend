package com.example.auth_service.rbac.service;

import com.example.auth_service.rbac.entity.Role;
import com.example.auth_service.rbac.repository.RoleRepository;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Call getEffectivePermissions(role) from AuthService.login() (3.2) when
 * building the JWT, instead of whatever placeholder was populating the
 * permissions[] claim before RBAC existed. This is the missing link between
 * the tenant/V2 roles table and the JWT claim that PermissionAspect reads.
 *
 * SUPER_ADMIN is handled separately — it never resolves through this
 * tenant-scoped repository (see V2 migration comment). If role == "SUPER_ADMIN",
 * short-circuit to a hardcoded wildcard permission set at the call site.
 */
@Service
public class PermissionResolutionService {

    private final RoleRepository roleRepository;

    public PermissionResolutionService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public Set<String> getEffectivePermissions(String roleName) {
        return roleRepository.findByName(roleName)
            .map(Role::getPermissions)
            .map(perms -> perms.stream()
                .map(p -> p.getCode())
                .collect(Collectors.toUnmodifiableSet()))
            .orElse(Set.of());
    }

    /**
     * User.permissions is a denormalized comma-separated string (see the
     * real entity), not a live join — this is the single place that flattens
     * a role's permission set into that format. Call this whenever a user's
     * role is set or changed (invite acceptance, future role-change endpoint).
     */
    public String getEffectivePermissionsAsCsv(String roleName) {
        return String.join(",", getEffectivePermissions(roleName));
    }
}
