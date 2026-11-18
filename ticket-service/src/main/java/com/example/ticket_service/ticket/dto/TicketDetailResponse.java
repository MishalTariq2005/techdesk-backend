package com.example.ticket_service.ticket.dto;

import java.util.List;

public record TicketDetailResponse(
    TicketResponse ticket,
    List<CommentResponse> comments,
    List<AssignmentLogResponse> assignmentHistory
) {}
