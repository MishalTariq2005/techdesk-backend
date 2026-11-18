package com.example.ticket_service.priority.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * One row = one compound rule: (category AND keyword AND department) -> priority.
 * A null field on category/keyword/department means "don't care" (wildcard) —
 * that's what lets a single table express both narrow rules (all three set)
 * and broad fallback rules (all three null) without a separate rule-type
 * discriminator column.
 *
 * `weight` determines evaluation order: PriorityEngineService picks the
 * HIGHEST-weight matching rule, not the first one found. Convention: give
 * more specific rules (more non-null fields) a higher weight than broad
 * ones — the engine doesn't enforce this automatically, it's an admin
 * authoring convention. If two matching rules tie on weight AND disagree
 * on priority, that's logged as an ambiguous match (see PriorityEngineService).
 */
@Entity
@Table(name = "priority_rules")
public class PriorityRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "category_id")
    private UUID categoryId; // null = matches any category

    private String keyword; // null = don't check description; otherwise case-insensitive "contains"

    @Column(name = "reporter_department")
    private String reporterDepartment; // null = matches any department

    @Column(nullable = false)
    private String priority; // CRITICAL | HIGH | MEDIUM | LOW

    @Column(nullable = false)
    private int weight = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PriorityRule() {}

    public PriorityRule(UUID categoryId, String keyword, String reporterDepartment, String priority, int weight) {
        this.categoryId = categoryId;
        this.keyword = keyword;
        this.reporterDepartment = reporterDepartment;
        this.priority = priority;
        this.weight = weight;
    }

    /** Number of non-wildcard conditions on this rule — used as a tie-break for equal-weight matches. */
    public int specificity() {
        int count = 0;
        if (categoryId != null) count++;
        if (keyword != null) count++;
        if (reporterDepartment != null) count++;
        return count;
    }

    public boolean matches(UUID candidateCategoryId, String description, String candidateDepartment) {
        if (categoryId != null && !categoryId.equals(candidateCategoryId)) return false;
        if (keyword != null) {
            if (description == null) return false;
            if (!description.toLowerCase().contains(keyword.toLowerCase())) return false;
        }
        if (reporterDepartment != null && !reporterDepartment.equalsIgnoreCase(candidateDepartment)) return false;
        return true;
    }

    public UUID getId() { return id; }
    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getReporterDepartment() { return reporterDepartment; }
    public void setReporterDepartment(String reporterDepartment) { this.reporterDepartment = reporterDepartment; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }
    public Instant getCreatedAt() { return createdAt; }
}
