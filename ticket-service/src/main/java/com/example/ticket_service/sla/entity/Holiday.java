package com.example.ticket_service.sla.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "holidays")
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "holiday_date", nullable = false, unique = true)
    private LocalDate holidayDate;

    @Column(length = 255)
    private String description;

    protected Holiday() {
    }

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public String getDescription() {
        return description;
    }
}
