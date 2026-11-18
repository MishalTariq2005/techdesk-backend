package com.example.ticket_service.sla.entity;

import jakarta.persistence.*;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "business_hours")
public class BusinessHours {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** ISO-8601: 1=Monday .. 7=Sunday — matches java.time.DayOfWeek.getValue(). */
    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected BusinessHours() {
    }

    public short getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }
}
