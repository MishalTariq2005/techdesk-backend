package com.example.ticket_service.priority.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record PriorityPreviewRequest(
    UUID categoryId,
    @NotBlank String description,
    String reporterDepartment
) {}
