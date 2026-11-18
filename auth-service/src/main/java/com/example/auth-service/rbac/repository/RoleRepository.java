package com.example.auth_service.rbac.repository;

import com.example.auth_service.rbac.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, java.util.UUID> {
    Optional<Role> findByName(String name);
}
