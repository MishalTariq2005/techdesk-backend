package com.example.ticket_service.sla.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sla_policies")
public class SlaPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "response_hours", nullable = false)
    private int responseHours;

    @Column(name = "resolution_hours", nullable = false)
    private int resolutionHours;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected SlaPolicy() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public int getResponseHours() {
        return responseHours;
    }

    public int getResolutionHours() {
        return resolutionHours;
    }
}
