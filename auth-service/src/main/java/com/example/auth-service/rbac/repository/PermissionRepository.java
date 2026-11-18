package com.example.auth_service.rbac.repository;

import com.example.auth_service.rbac.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, java.util.UUID> {
}
