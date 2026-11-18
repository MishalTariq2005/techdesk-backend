package com.example.ticket_service.sla.repository;

import com.example.ticket_service.sla.entity.BusinessHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BusinessHoursRepository extends JpaRepository<BusinessHours, UUID> {
}
