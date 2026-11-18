package com.example.ticket_service.assignment.dto;

import java.util.List;
import java.util.UUID;

public record AssignmentExplanationResponse(
    UUID ticketId,
    UUID assignedTo,
    List<CandidateScoreDto> candidates, // every candidate considered, sorted by totalScore desc
    String summary
) {}
