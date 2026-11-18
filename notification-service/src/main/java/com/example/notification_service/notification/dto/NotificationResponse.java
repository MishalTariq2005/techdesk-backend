package com.example.notification_service.notification.dto;

import com.example.notification_service.notification.entity.NotificationParentType;
import com.example.notification_service.notification.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    NotificationType type,
    String title,
    String message,
    NotificationParentType parentType,
    UUID parentId,
    boolean read,
    Instant readAt,
    Instant createdAt
) {}
