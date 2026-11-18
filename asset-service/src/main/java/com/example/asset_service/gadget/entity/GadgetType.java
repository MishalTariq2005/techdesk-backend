package com.example.asset_service.gadget.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * The catalog Company Admin configures per 5.2 ("Company Admin can configure
 * whether manager approval is required per gadget type"). Deliberately
 * separate from Asset itself — GadgetType is a category of thing that can be
 * requested (e.g. "Laptop"), Asset is a specific physical unit. The link
 * between them is by name/type string only (see GadgetRequestService),
 * there's no FK — same reasoning as everywhere else in this project that a
 * formal reference isn't drawn across a boundary that doesn't strictly need
 * one yet.
 */
@Entity
@Table(name = "gadget_types")
public class GadgetType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected GadgetType() {}

    public GadgetType(String name, String description, boolean requiresApproval) {
        this.name = name;
        this.description = description;
        this.requiresApproval = requiresApproval;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(boolean requiresApproval) { this.requiresApproval = requiresApproval; }
    public Instant getCreatedAt() { return createdAt; }
}
