package com.example.notification_service.notification.service;

import com.example.notification_service.multitenancy.TenantContext;
import com.example.notification_service.notification.dto.NotificationResponse;
import com.example.notification_service.notification.dto.PagedNotificationResponse;
import com.example.notification_service.notification.entity.Notification;
import com.example.notification_service.notification.exception.NotificationNotFoundException;
import com.example.notification_service.notification.repository.NotificationRepository;
import com.example.notification_service.notification.sse.SseEmitterRegistry;
import com.example.notification_service.rbac.security.CurrentUserContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@Service
public class NotificationQueryService {

    private final NotificationRepository repository;
    private final SseEmitterRegistry emitterRegistry;

    public NotificationQueryService(NotificationRepository repository, SseEmitterRegistry emitterRegistry) {
        this.repository = repository;
        this.emitterRegistry = emitterRegistry;
    }

    /** Always the CALLER's own notifications — no recipientUserId query
     * param exposed, so one user can never list another's. */
    public PagedNotificationResponse listMine(int page, int size) {
        UUID callerId = currentUserId();
        Page<Notification> result = repository.findByRecipientUserIdOrderByCreatedAtDesc(
            callerId, PageRequest.of(page, size));
        return new PagedNotificationResponse(
            result.getContent().stream().map(this::toResponse).toList(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId) {
        UUID callerId = currentUserId();
        Notification notification = repository.findById(notificationId)
            .orElseThrow(() -> new NotificationNotFoundException("Notification not found: " + notificationId));

        // 404, not 403, for a notification that belongs to someone else —
        // same "don't confirm existence" reasoning as
        // TicketService.getAndCheckVisibility.
        if (!notification.getRecipientUserId().equals(callerId)) {
            throw new NotificationNotFoundException("Notification not found: " + notificationId);
        }

        notification.markRead();
        return toResponse(notification);
    }

    /**
     * Captures tenant+user ONCE here, into local variables, BEFORE
     * returning the emitter — TenantContext/CurrentUserContext are
     * ThreadLocals cleared by the filter chain's finally block as soon as
     * this method returns (Spring's async SSE handling doesn't keep this
     * request thread blocked for the connection's lifetime), so nothing
     * later in the connection's life can re-read them. The registry key is
     * fixed at connection-open time and never needs to change.
     */
    public SseEmitter openStream() {
        String tenantId = TenantContext.getCurrentTenant();
        String userId = CurrentUserContext.get().userId();
        return emitterRegistry.register(tenantId, userId);
    }

    private UUID currentUserId() {
        return UUID.fromString(CurrentUserContext.get().userId());
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
            n.getParentType(), n.getParentId(), n.isRead(), n.getReadAt(), n.getCreatedAt());
    }
}
