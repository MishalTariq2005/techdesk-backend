package com.example.notification_service.notification.sse;

import com.example.notification_service.notification.entity.NotificationParentType;
import com.example.notification_service.notification.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

/**
 * The full notification payload travels IN the Redis Pub/Sub message,
 * rather than just an id — avoids every subscribing instance needing its
 * own DB round-trip (in the right tenant schema, no less) just to find out
 * whether the event is even relevant to a connection it holds locally.
 */
public record NotificationEventPayload(
    String tenantId,
    UUID recipientUserId,
    UUID notificationId,
    NotificationType type,
    String title,
    String message,
    NotificationParentType parentType,
    UUID parentId,
    Instant createdAt
) {}
