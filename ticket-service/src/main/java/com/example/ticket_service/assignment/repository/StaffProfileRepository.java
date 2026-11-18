package com.example.ticket_service.assignment.repository;

import com.example.ticket_service.assignment.entity.StaffProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, UUID> {
    List<StaffProfile> findByAvailableTrue();
}
