package com.example.auth_service.repository;

import com.example.auth_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

/**
 * If you already have a UserRepository (you must, for login to work),
 * just add "extends JpaSpecificationExecutor<User>" to it instead of
 * dropping this file in — Specification support is what makes the
 * optional role/department/status filters in listUsers() composable.
 */
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
