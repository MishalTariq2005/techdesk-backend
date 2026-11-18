package com.example.ticket_service.priority.dto;

import java.time.Instant;
import java.util.UUID;

public record PriorityRuleResponse(
    UUID id, UUID categoryId, String keyword, String reporterDepartment,
    String priority, int weight, Instant createdAt
) {}
