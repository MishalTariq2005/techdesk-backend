package com.example.ticket_service.ticket.dto;

import java.util.List;

public record PagedTicketResponse(
    List<TicketResponse> content, int page, int size, long totalElements, int totalPages
) {}
