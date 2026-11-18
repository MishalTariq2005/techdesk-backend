package com.example.ticket_service.assignment.dto;

import java.util.UUID;

public record StaffProfileResponse(
    UUID staffUserId, boolean available, String location, int maxActiveTickets, long currentActiveTicketCount
) {}
