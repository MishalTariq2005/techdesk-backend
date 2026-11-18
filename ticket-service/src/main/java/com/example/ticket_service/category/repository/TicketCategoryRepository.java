package com.example.ticket_service.category.repository;

import com.example.ticket_service.category.entity.TicketCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TicketCategoryRepository extends JpaRepository<TicketCategory, UUID> {
    boolean existsByNameIgnoreCase(String name);
}
