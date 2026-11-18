package com.example.ticket_service.assignment.dto;

import java.util.UUID;

/** One candidate's full score breakdown — this is what gets JSON-serialized into ticket_assignment_logs.score_breakdown. */
public record CandidateScoreDto(
    UUID staffUserId,
    double skillMatchScore,     // 0-40
    double availabilityScore,   // 0-25
    double locationMatch,       // 0-15
    double workloadPenalty,     // 0-5 (subtracted)
    double priorityExperience,  // 0-20
    double totalScore,
    boolean skippedForBurst,
    long activeTicketCount
) {}
