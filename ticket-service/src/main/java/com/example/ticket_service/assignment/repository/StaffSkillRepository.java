package com.example.ticket_service.assignment.repository;

import com.example.ticket_service.assignment.entity.StaffSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffSkillRepository extends JpaRepository<StaffSkill, UUID> {
    Optional<StaffSkill> findByStaffUserIdAndCategoryId(UUID staffUserId, UUID categoryId);
    List<StaffSkill> findByStaffUserId(UUID staffUserId);
}
