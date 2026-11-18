package com.example.ticket_service.assignment.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "staff_profiles")
public class StaffProfile {

    @Id
    @Column(name = "staff_user_id")
    private UUID staffUserId;

    @Column(nullable = false)
    private boolean available = true;

    private String location;

    @Column(name = "max_active_tickets", nullable = false)
    private int maxActiveTickets = 15;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    protected StaffProfile() {}

    public StaffProfile(UUID staffUserId, boolean available, String location, int maxActiveTickets) {
        this.staffUserId = staffUserId;
        this.available = available;
        this.location = location;
        this.maxActiveTickets = maxActiveTickets;
    }

    public UUID getStaffUserId() { return staffUserId; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; this.updatedAt = Instant.now(); }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; this.updatedAt = Instant.now(); }
    public int getMaxActiveTickets() { return maxActiveTickets; }
    public void setMaxActiveTickets(int maxActiveTickets) { this.maxActiveTickets = maxActiveTickets; this.updatedAt = Instant.now(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
