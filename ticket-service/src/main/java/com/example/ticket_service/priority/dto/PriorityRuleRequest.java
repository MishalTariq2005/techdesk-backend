package com.example.ticket_service.priority.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record PriorityRuleRequest(
    UUID categoryId,        // nullable = wildcard
    String keyword,         // nullable = wildcard
    String reporterDepartment, // nullable = wildcard
    @NotBlank String priority, // CRITICAL | HIGH | MEDIUM | LOW
    int weight
) {}
