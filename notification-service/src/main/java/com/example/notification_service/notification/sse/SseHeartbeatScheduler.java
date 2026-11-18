package com.example.notification_service.notification.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;

/**
 * SSE connections proxied through anything (the API Gateway, corporate
 * proxies, load balancers) can get silently closed if idle too long — a
 * comment-only heartbeat is the standard way to keep the connection alive
 * and let a dead connection's cleanup fire sooner than waiting for an actual
 * notification to attempt delivery and fail. WORTH VERIFYING EMPIRICALLY:
 * this project's api-gateway is Spring Cloud Gateway (Reactor Netty, which
 * handles streaming responses natively), so proxied SSE SHOULD work, but
 * this hasn't been tested through the gateway specifically yet — test both
 * direct-to-service (8086) and through the gateway (8080) to confirm.
 */
@Component
public class SseHeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(SseHeartbeatScheduler.class);

    private final SseEmitterRegistry registry;

    public SseHeartbeatScheduler(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    @Scheduled(fixedRate = 30000)
    public void sendHeartbeats() {
        Map<String, Set<SseEmitter>> all = registry.allLocalConnections();
        for (Map.Entry<String, Set<SseEmitter>> entry : all.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (Exception e) {
                    log.debug("Heartbeat failed for {} — connection likely already dead, cleanup will follow", entry.getKey());
                }
            }
        }
    }
}
