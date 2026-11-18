package com.example.ticket_service.assignment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UpsertStaffSkillRequest(
    @NotNull UUID categoryId,
    @Min(1) @Max(5) int skillLevel
) {}
