package com.example.ticket_service.ticket.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Roadmap 4.3: "Every status change must write an entry to
 * ticket_assignment_logs and audit_logs." audit_logs doesn't exist until
 * 6.3 — status changes are SLF4J-logged in the meantime (same TODO pattern
 * as PriorityEngineService's conflict logging from 4.2). This table is the
 * real, queryable record for now; it also doubles as "assignment history"
 * for the GET /api/tickets/{id} detail view once 4.4 starts writing
 * assignment events here too (assignedTo populated, fromStatus/toStatus null).
 */
@Entity
@Table(name = "ticket_assignment_logs")
public class TicketAssignmentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "actor_id")
    private UUID actorId; // null = system-initiated (e.g. future auto-assignment)

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status")
    private String toStatus;

    @Column(name = "assigned_to")
    private UUID assignedTo; // populated for assignment events, null for pure status changes

    private String reason;

    /** Full JSON scoring breakdown for auto-assignment decisions (4.4). Null for pure human-initiated status changes. */
    @Column(name = "score_breakdown", columnDefinition = "TEXT")
    private String scoreBreakdown;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected TicketAssignmentLog() {}

    public static TicketAssignmentLog statusChange(UUID ticketId, UUID actorId, String from, String to, String reason) {
        TicketAssignmentLog log = new TicketAssignmentLog();
        log.ticketId = ticketId;
        log.actorId = actorId;
        log.fromStatus = from;
        log.toStatus = to;
        log.reason = reason;
        return log;
    }

    public static TicketAssignmentLog autoAssignment(UUID ticketId, UUID assignedTo, String from, String to,
                                                        String reason, String scoreBreakdown) {
        TicketAssignmentLog log = new TicketAssignmentLog();
        log.ticketId = ticketId;
        log.actorId = null; // system-initiated
        log.assignedTo = assignedTo;
        log.fromStatus = from;
        log.toStatus = to;
        log.reason = reason;
        log.scoreBreakdown = scoreBreakdown;
        return log;
    }

    public String getScoreBreakdown() { return scoreBreakdown; }

    public UUID getId() { return id; }
    public UUID getTicketId() { return ticketId; }
    public UUID getActorId() { return actorId; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public UUID getAssignedTo() { return assignedTo; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
