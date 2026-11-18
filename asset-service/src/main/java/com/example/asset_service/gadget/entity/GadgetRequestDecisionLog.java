package com.example.asset_service.gadget.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * The audit-replay backbone for 5.2's challenge: "given the audit log for a
 * gadget request, an external reviewer must be able to reconstruct the exact
 * decision timeline... with no information gaps." Every single status
 * transition — human-triggered or system-triggered — gets its own row here.
 * Same role TicketAssignmentLog plays for tickets, AssetAssignmentHistory for
 * assets: one append-only table, one query, full timeline, no joins needed
 * to reconstruct "what happened and when."
 *
 * actorUserId is nullable specifically for system-triggered transitions —
 * e.g. the initial REQUESTED->PENDING_MANAGER_APPROVAL routing, or the
 * atomic APPROVED->READY_FOR_PICKUP->ASSIGNED chain steps that happen inside
 * one approve() call. A null actor here is meaningful, not a data gap: it
 * tells the reviewer "the system did this automatically," which is itself
 * part of the reconstructable timeline.
 */
@Entity
@Table(name = "gadget_request_decision_log")
public class GadgetRequestDecisionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "gadget_request_id", nullable = false)
    private UUID gadgetRequestId;

    @Column(name = "from_status", length = 30)
    private String fromStatus;   // null for the very first REQUESTED row

    @Column(name = "to_status", nullable = false, length = 30)
    private String toStatus;

    @Column(name = "actor_user_id")
    private UUID actorUserId;    // null = system-triggered, not a human decision

    @Column(length = 500)
    private String reason;

    @Column(name = "asset_id")
    private UUID assetId;        // populated only on the transition that actually assigns an asset

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp = Instant.now();

    protected GadgetRequestDecisionLog() {}

    public GadgetRequestDecisionLog(UUID gadgetRequestId, String fromStatus, String toStatus,
                                     UUID actorUserId, String reason, UUID assetId) {
        this.gadgetRequestId = gadgetRequestId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorUserId = actorUserId;
        this.reason = reason;
        this.assetId = assetId;
    }

    public UUID getId() { return id; }
    public UUID getGadgetRequestId() { return gadgetRequestId; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public UUID getActorUserId() { return actorUserId; }
    public String getReason() { return reason; }
    public UUID getAssetId() { return assetId; }
    public Instant getEventTimestamp() { return eventTimestamp; }
}
