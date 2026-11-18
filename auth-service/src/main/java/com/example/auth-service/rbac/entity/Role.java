package com.example.auth_service.rbac.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * NO @Table(schema=...) — floats with Hibernate's dynamic schema switch,
 * same pattern as {@code User}. Resolves to whatever tenant schema is
 * active in TenantContext for the current request.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    private String description;

    @Column(name = "is_system_role")
    private boolean systemRole = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public boolean isSystemRole() { return systemRole; }
    public Set<Permission> getPermissions() { return permissions; }
}
