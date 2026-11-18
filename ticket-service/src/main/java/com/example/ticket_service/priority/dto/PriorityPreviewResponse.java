package com.example.ticket_service.priority.dto;

import java.util.List;
import java.util.UUID;

public record PriorityPreviewResponse(
    String priority,
    UUID matchedRuleId,
    boolean ambiguous,
    List<UUID> conflictingRuleIds,
    String explanation
) {}
