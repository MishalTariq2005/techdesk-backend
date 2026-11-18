package com.example.asset_service.gadget.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the reconstructable timeline for 5.2's audit-replay challenge.
 * actorUserId null means the transition was system-triggered, not a gap in
 * the data — see GadgetRequestDecisionLog's own doc comment.
 */
public record GadgetRequestDecisionResponse(
    String fromStatus,
    String toStatus,
    UUID actorUserId,
    String reason,
    UUID assetId,
    Instant eventTimestamp
) {}
