package com.example.ticket_service.sla.repository;

import com.example.ticket_service.sla.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {
    List<Holiday> findByHolidayDateBetween(LocalDate start, LocalDate end);
}
