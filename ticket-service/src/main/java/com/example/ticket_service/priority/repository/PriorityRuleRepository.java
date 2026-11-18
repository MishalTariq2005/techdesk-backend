package com.example.ticket_service.priority.repository;

import com.example.ticket_service.priority.entity.PriorityRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PriorityRuleRepository extends JpaRepository<PriorityRule, UUID> {
}
