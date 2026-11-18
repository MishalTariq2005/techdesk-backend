-- V13: 5.4 — Notification Service (new standalone notification-service,
-- same reasoning as 5.3's file-service: this needs to be triggered by
-- events from BOTH ticket-service and asset-service, so no single existing
-- service already owns the majority of these events).
--
-- Same convention as every migration since V7: lives in tenant-service's
-- set even though the code lives in notification-service.

CREATE TABLE notifications (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_user_id  UUID NOT NULL,   -- no FK: users live in auth-service's schema
    type               VARCHAR(30) NOT NULL,   -- TICKET_CREATED | TICKET_ASSIGNED | TICKET_RESOLVED |
                                                 -- COMMENT_ADDED | GADGET_REQUEST_APPROVED |
                                                 -- GADGET_REQUEST_REJECTED | SLA_BREACH_WARNING (unwired until 6.1)
    title              VARCHAR(255) NOT NULL,
    message            VARCHAR(1000) NOT NULL,
    parent_type        VARCHAR(20),    -- 'TICKET' | 'GADGET_REQUEST', nullable — not every
                                         -- notification has a specific deep-link target
    parent_id          UUID,
    read               BOOLEAN NOT NULL DEFAULT FALSE,
    read_at            TIMESTAMP,
    created_at         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_recipient ON notifications(recipient_user_id, created_at DESC);
