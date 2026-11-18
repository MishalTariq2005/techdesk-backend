package com.example.ticket_service.ticket.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateStatusRequest(@NotBlank String newStatus, String reason) {}
