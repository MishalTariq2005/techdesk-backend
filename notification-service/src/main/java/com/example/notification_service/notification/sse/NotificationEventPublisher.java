package com.example.notification_service.notification.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Every instance publishes here after persisting a notification (see
 * NotificationDispatchService) — including the instance that might itself
 * hold the target connection. That instance's OWN subscriber will receive
 * this same message back and push it locally; there's no special-cased
 * "check locally first" shortcut, keeping exactly one code path regardless
 * of which instance (if any) actually owns the live connection.
 */
@Component
public class NotificationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventPublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String channel;

    public NotificationEventPublisher(StringRedisTemplate redisTemplate,
                                       ObjectMapper objectMapper,
                                       @Value("${app.notifications.redis-channel}") String channel) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.channel = channel;
    }

    public void publish(NotificationEventPayload payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.convertAndSend(channel, json);
        } catch (Exception e) {
            // Best-effort, same as email sending — an SSE push failure must
            // never fail the notification-creation request itself. The
            // notification row is already persisted regardless; the
            // recipient will still see it next time they load
            // GET /api/notifications, just without the real-time push.
            log.warn("Failed to publish SSE event for notification {} — recipient will still see it via GET /api/notifications",
                payload.notificationId(), e);
        }
    }
}
