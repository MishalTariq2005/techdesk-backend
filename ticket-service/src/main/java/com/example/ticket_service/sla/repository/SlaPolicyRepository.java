package com.example.ticket_service.sla.repository;

import com.example.ticket_service.sla.entity.SlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, UUID> {
    Optional<SlaPolicy> findByCategoryId(UUID categoryId);
}
