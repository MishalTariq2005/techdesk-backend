package com.example.notification_service.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "parent_type", length = 20)
    private NotificationParentType parentType;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Notification() {}

    public Notification(UUID recipientUserId, NotificationType type, String title, String message,
                         NotificationParentType parentType, UUID parentId) {
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.parentType = parentType;
        this.parentId = parentId;
    }

    public UUID getId() { return id; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public NotificationParentType getParentType() { return parentType; }
    public UUID getParentId() { return parentId; }
    public boolean isRead() { return read; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void markRead() {
        this.read = true;
        this.readAt = Instant.now();
    }
}
