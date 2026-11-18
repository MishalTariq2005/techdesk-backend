package com.example.ticket_service.ticket.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_comments")
public class TicketComment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false)
    private String content;

    /** true = internal note, visible only to IT staff/manager/admin. false = visible to the employee too. */
    @Column(nullable = false)
    private boolean internal = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected TicketComment() {}

    public TicketComment(UUID ticketId, UUID authorId, String content, boolean internal) {
        this.ticketId = ticketId;
        this.authorId = authorId;
        this.content = content;
        this.internal = internal;
    }

    public UUID getId() { return id; }
    public UUID getTicketId() { return ticketId; }
    public UUID getAuthorId() { return authorId; }
    public String getContent() { return content; }
    public boolean isInternal() { return internal; }
    public Instant getCreatedAt() { return createdAt; }
}
