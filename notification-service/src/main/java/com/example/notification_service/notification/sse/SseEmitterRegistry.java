package com.example.notification_service.notification.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Purely LOCAL to this JVM instance — holds only the SseEmitters this exact
 * process currently has open. Cross-instance delivery is Redis Pub/Sub's job
 * (see NotificationEventSubscriber), not this class's. Key is
 * "tenantId:userId", not just userId — this is what makes the SSE
 * connection tenant-scoped as the 5.4 challenge requires: an event
 * published for tenant_acme's user X can only ever match an emitter
 * registered under that EXACT tenant+user key, never a same-userId
 * collision across tenants (which shouldn't happen given UUIDs, but the
 * tenant-scoped key is correct hygiene regardless, and makes the intent
 * explicit in code rather than relying on UUID collision odds).
 *
 * A Set per key (not a single emitter) supports multiple simultaneous
 * connections for the same user — e.g. two browser tabs open at once.
 */
@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    private final ConcurrentHashMap<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String tenantId, String userId) {
        String key = key(tenantId, userId);
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // client (EventSource) handles reconnect on timeout/drop

        emitters.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(emitter);

        Runnable cleanup = () -> {
            Set<SseEmitter> set = emitters.get(key);
            if (set != null) {
                set.remove(emitter);
                if (set.isEmpty()) {
                    emitters.remove(key, set);
                }
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        log.info("SSE connection registered for {} (now {} local connection(s) for this key)",
            key, emitters.getOrDefault(key, Set.of()).size());
        return emitter;
    }

    /**
     * Pushes to every LOCAL emitter registered for this exact tenant+user —
     * called only by NotificationEventSubscriber, never directly by
     * dispatch logic (which always goes through Redis Pub/Sub first, even
     * for the same instance that persisted the notification — keeps
     * exactly one code path regardless of which instance ends up owning
     * the connection).
     */
    public void pushToLocalConnections(String tenantId, String userId, String eventName, String jsonPayload) {
        String key = key(tenantId, userId);
        Set<SseEmitter> set = emitters.get(key);
        if (set == null || set.isEmpty()) {
            return; // nothing local for this key — some other instance may own it, or nobody's connected
        }
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(jsonPayload));
            } catch (Exception e) {
                log.warn("Failed to push SSE event to a connection for {} — dropping it", key, e);
                emitter.completeWithError(e);
                set.remove(emitter);
            }
        }
    }

    /** Used by the heartbeat scheduler — see SseHeartbeatScheduler. */
    public java.util.Map<String, Set<SseEmitter>> allLocalConnections() {
        return emitters;
    }

    private String key(String tenantId, String userId) {
        return tenantId + ":" + userId;
    }
}
