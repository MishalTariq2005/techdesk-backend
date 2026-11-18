package com.example.notification_service.notification.controller;

import com.example.notification_service.notification.dto.CreateNotificationRequest;
import com.example.notification_service.notification.dto.NotificationResponse;
import com.example.notification_service.notification.dto.PagedNotificationResponse;
import com.example.notification_service.notification.service.NotificationDispatchService;
import com.example.notification_service.notification.service.NotificationQueryService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationDispatchService dispatchService;
    private final NotificationQueryService queryService;

    public NotificationController(NotificationDispatchService dispatchService,
                                   NotificationQueryService queryService) {
        this.dispatchService = dispatchService;
        this.queryService = queryService;
    }

    /**
     * The internal "an event happened" ingestion endpoint — called by
     * ticket-service/asset-service, not by end users via a UI. Deliberately
     * NOT @RequiresPermission-gated: the caller forwards the ACTING staff
     * member's own JWT (whoever triggered the underlying event), not the
     * recipient's — that person has no particular reason to hold any
     * notification-specific permission. Same "trust the caller, no
     * dedicated service-to-service auth scheme" posture already used
     * project-wide (see auth-service's own open items: permitAll() +
     * opt-in @RequiresPermission, not deny-by-default).
     */
    @PostMapping
    public ResponseEntity<NotificationResponse> create(@Valid @RequestBody CreateNotificationRequest request) {
        return ResponseEntity.ok(dispatchService.dispatch(request));
    }

    @GetMapping
    public ResponseEntity<PagedNotificationResponse> listMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(queryService.listMine(page, size));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable UUID id) {
        return ResponseEntity.ok(queryService.markRead(id));
    }

    /**
     * The 5.4 challenge's actual deliverable — real-time push, no polling.
     * text/event-stream, kept open indefinitely (see SseEmitterRegistry);
     * client (EventSource) handles reconnect automatically on drop/timeout.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return queryService.openStream();
    }
}
