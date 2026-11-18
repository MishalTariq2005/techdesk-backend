package com.example.ticket_service.ticket.dto;

import java.time.Instant;
import java.util.UUID;

public record TicketResponse(
    UUID id, UUID categoryId, String title, String description, String status,
    String priority, UUID createdBy, String reporterDepartment, UUID assignedTo,
    long version, Instant createdAt, Instant updatedAt,
    Instant slaDueAt, boolean slaBreached
) {}
