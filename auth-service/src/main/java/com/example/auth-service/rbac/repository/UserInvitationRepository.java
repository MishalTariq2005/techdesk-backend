package com.example.auth_service.rbac.repository;

import com.example.auth_service.rbac.entity.UserInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserInvitationRepository extends JpaRepository<UserInvitation, java.util.UUID> {
    Optional<UserInvitation> findByTokenHashAndStatus(String tokenHash, String status);
    Optional<UserInvitation> findByEmailAndStatus(String email, String status);
}
