package com.example.ticket_service.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateTicketRequest(
    @NotNull UUID categoryId,
    @NotBlank String title,
    @NotBlank String description,
    String reporterDepartment
) {}
