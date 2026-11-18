package com.example.notification_service.notification.service;

import com.example.notification_service.multitenancy.TenantContext;
import com.example.notification_service.notification.dto.CreateNotificationRequest;
import com.example.notification_service.notification.dto.NotificationResponse;
import com.example.notification_service.notification.entity.Notification;
import com.example.notification_service.notification.repository.NotificationRepository;
import com.example.notification_service.notification.sse.NotificationEventPayload;
import com.example.notification_service.notification.sse.NotificationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * This is the endpoint ticket-service/asset-service call (fire-and-forget
 * from THEIR side — see their own NotificationClient's try/catch). Once a
 * request actually reaches here, though, only the DB persist is load-
 * bearing; email and SSE push are both best-effort on top of it (see
 * EmailNotificationService and NotificationEventPublisher's own doc
 * comments) — a notification that fails to email or push still exists and
 * is still visible via GET /api/notifications.
 */
@Service
public class NotificationDispatchService {

    private final NotificationRepository repository;
    private final EmailNotificationService emailService;
    private final NotificationEventPublisher eventPublisher;

    public NotificationDispatchService(NotificationRepository repository,
                                        EmailNotificationService emailService,
                                        NotificationEventPublisher eventPublisher) {
        this.repository = repository;
        this.emailService = emailService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public NotificationResponse dispatch(CreateNotificationRequest request) {
        Notification saved = repository.save(new Notification(
            request.recipientUserId(), request.type(), request.title(), request.message(),
            request.parentType(), request.parentId()));

        emailService.sendNotificationEmail(request.recipientUserId(), request.title(), request.message());

        eventPublisher.publish(new NotificationEventPayload(
            TenantContext.getCurrentTenant(), saved.getRecipientUserId(), saved.getId(),
            saved.getType(), saved.getTitle(), saved.getMessage(),
            saved.getParentType(), saved.getParentId(), saved.getCreatedAt()));

        return toResponse(saved);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
            n.getParentType(), n.getParentId(), n.isRead(), n.getReadAt(), n.getCreatedAt());
    }
}
