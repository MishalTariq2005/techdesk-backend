package com.example.ticket_service.assignment.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "staff_skills")
public class StaffSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "staff_user_id", nullable = false)
    private UUID staffUserId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    /** 1-5. Scaled to a 0-40 point contribution (skillLevel/5.0 * 40) by the scoring engine. */
    @Column(name = "skill_level", nullable = false)
    private int skillLevel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected StaffSkill() {}

    public StaffSkill(UUID staffUserId, UUID categoryId, int skillLevel) {
        this.staffUserId = staffUserId;
        this.categoryId = categoryId;
        this.skillLevel = skillLevel;
    }

    public UUID getId() { return id; }
    public UUID getStaffUserId() { return staffUserId; }
    public UUID getCategoryId() { return categoryId; }
    public int getSkillLevel() { return skillLevel; }
    public void setSkillLevel(int skillLevel) { this.skillLevel = skillLevel; }
    public Instant getCreatedAt() { return createdAt; }
}
