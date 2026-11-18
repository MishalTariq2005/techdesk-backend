package com.example.notification_service.notification.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * FIX: originally wired up via MessageListenerAdapter(this, "onMessage"),
 * which needs Spring's afterPropertiesSet() lifecycle callback to resolve
 * that method name into a real invoker via reflection — since the adapter
 * was constructed with plain `new` inside a @Bean method rather than
 * registered as its own bean, that callback never ran, leaving its
 * internal invoker permanently null and crashing with an NPE the moment
 * Redis delivered a real message. Implementing MessageListener directly
 * sidesteps the whole reflection mechanism — nothing to misconfigure.
 */
@Component
public class NotificationEventSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventSubscriber.class);

    private final SseEmitterRegistry registry;
    private final ObjectMapper objectMapper;

    public NotificationEventSubscriber(SseEmitterRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String jsonPayload = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            NotificationEventPayload payload = objectMapper.readValue(jsonPayload, NotificationEventPayload.class);
            registry.pushToLocalConnections(
                payload.tenantId(),
                payload.recipientUserId().toString(),
                "notification",
                jsonPayload
            );
        } catch (Exception e) {
            log.warn("Failed to process incoming SSE event from Redis — dropping it", e);
        }
    }
}