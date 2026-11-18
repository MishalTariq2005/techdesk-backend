package com.example.asset_service.asset.exception;

import java.time.Instant;
import java.util.UUID;

/**
 * Carries the structured detail the 5.1 challenge asks for (current
 * assignee's name + assignment date) — same "exception carries the conflict
 * state" pattern as ticket-service's TicketConflictException/currentState.
 *
 * currentAssigneeName is nullable: see UserLookupClient — if the live call
 * to auth-service fails for any reason, this falls back to null rather than
 * turning a routine 409 into an unrelated 500.
 */
public class AssetAlreadyAssignedException extends RuntimeException {

    private final UUID currentAssigneeId;
    private final String currentAssigneeName;
    private final Instant assignedAt;

    public AssetAlreadyAssignedException(String message, UUID currentAssigneeId,
                                          String currentAssigneeName, Instant assignedAt) {
        super(message);
        this.currentAssigneeId = currentAssigneeId;
        this.currentAssigneeName = currentAssigneeName;
        this.assignedAt = assignedAt;
    }

    public UUID getCurrentAssigneeId() { return currentAssigneeId; }
    public String getCurrentAssigneeName() { return currentAssigneeName; }
    public Instant getAssignedAt() { return assignedAt; }
}
