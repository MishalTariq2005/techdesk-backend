package com.example.ticket_service.assignment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UpsertStaffProfileRequest(
    @NotNull UUID staffUserId,
    boolean available,
    String location,
    @Min(1) int maxActiveTickets
) {}
