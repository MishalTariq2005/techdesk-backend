package com.example.auth_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * REPLACES your current version. Adds mapping for first_name, last_name,
 * status, must_change_password, department, updated_at — all already exist
 * in the tenant/V1 users table (per your 3.3 migration) except department,
 * which needs the new tenant/V5 migration below. They were columns in the
 * DB already; this was just never mapped in the entity because nothing
 * needed them until user management/invites (4.1) required them.
 *
 * Setters added for role/status/permissions/department — the original
 * entity was correctly immutable-after-creation for a login-only entity,
 * but 4.1 needs to mutate these (accepting an invite sets status ACTIVE
 * and permissions; a future admin action changes role).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(nullable = false)
    private String role;

    @Column(name = "department")
    private String department;

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING | ACTIVE | SUSPENDED

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    @Column(name = "permissions")
    private String permissions;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    protected User() {
        // required by JPA
    }

    /** Original login-path constructor — unchanged, still works for existing callers. */
    public User(String email, String passwordHash, String role, String permissions) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.permissions = permissions;
    }

    /** Used by InvitationService when an invite is accepted. */
    public static User fromAcceptedInvite(String email, String passwordHash, String firstName,
                                           String lastName, String role, String department,
                                           String permissions) {
        User user = new User(email, passwordHash, role, permissions);
        user.firstName = firstName;
        user.lastName = lastName;
        user.department = department;
        user.status = "ACTIVE";
        user.mustChangePassword = false;
        return user;
    }

    /** Splits the comma-separated permissions column into a list for JWT claims. */
    public List<String> getPermissionsList() {
        if (permissions == null || permissions.isBlank()) {
            return List.of();
        }
        return Arrays.stream(permissions.split(","))
                .map(String::trim)
                .toList();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDepartment() {
        return department;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
