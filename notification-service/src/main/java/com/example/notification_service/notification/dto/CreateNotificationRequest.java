package com.example.notification_service.notification.dto;

import com.example.notification_service.notification.entity.NotificationParentType;
import com.example.notification_service.notification.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * The calling service (ticket-service, asset-service) constructs the
 * human-readable title/message itself before calling this — notification-
 * service doesn't look up ticket/gadget-request context on its own, it only
 * needs an email address for the recipient (see UserLookupClient).
 * parentType/parentId are optional (null for a notification with no
 * specific deep-link target).
 */
public record CreateNotificationRequest(
    @NotNull UUID recipientUserId,
    @NotNull NotificationType type,
    @NotBlank String title,
    @NotBlank String message,
    NotificationParentType parentType,
    UUID parentId
) {}
