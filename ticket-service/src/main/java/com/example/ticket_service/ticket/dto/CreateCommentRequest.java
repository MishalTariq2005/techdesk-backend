package com.example.ticket_service.ticket.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCommentRequest(@NotBlank String content, boolean internal) {}
