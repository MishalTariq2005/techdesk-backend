package com.example.ticket_service.ticket.dto;

import java.time.Instant;
import java.util.UUID;

public record AssignmentLogResponse(
    UUID id, UUID actorId, String fromStatus, String toStatus, UUID assignedTo, String reason, Instant createdAt
) {}
