-- V14: 6.1 SLA Engine schema. Owned by tenant-service per the established
-- convention (V1-V13), even though ticket-service is the runtime owner.
--
-- Two SLA clocks per category (response + resolution), a per-tenant working-
-- hours calendar (the 6.1 challenge), and an optional holiday list. sla_due_at
-- on tickets tracks the RESOLUTION deadline specifically — response-SLA
-- tracking is deliberately out of scope here (see ticket-service's SlaService
-- javadoc for why) so this migration only adds what's actually wired up.

CREATE TABLE sla_policies (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id       UUID NOT NULL REFERENCES ticket_categories(id) ON DELETE CASCADE,
    response_hours    INTEGER NOT NULL,
    resolution_hours  INTEGER NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (category_id)
);

-- One row per (day_of_week, tenant). day_of_week: 1=Monday .. 7=Sunday
-- (ISO-8601, matches java.time.DayOfWeek.getValue()). A day with no row is
-- treated as non-working (e.g. a tenant that simply omits Saturday/Sunday).
CREATE TABLE business_hours (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    day_of_week  SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time   TIME NOT NULL,
    end_time     TIME NOT NULL,
    UNIQUE (day_of_week)
);

CREATE TABLE holidays (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holiday_date DATE NOT NULL UNIQUE,
    description  VARCHAR(255)
);

ALTER TABLE tickets
    ADD COLUMN sla_due_at        TIMESTAMP,
    ADD COLUMN sla_breached      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN sla_warning_sent  BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_tickets_sla_due_at ON tickets(sla_due_at);
CREATE INDEX idx_tickets_sla_breached ON tickets(sla_breached);

-- Default working hours: Mon-Fri 09:00-18:00, matching the roadmap's own
-- example (09:00-18:00 PKT, Mon-Fri). Tenants can override via a future
-- admin endpoint; this is just a sane seed so the SLA engine has something
-- to compute against out of the box instead of silently having zero
-- working hours (which would mean SLA timers never tick at all).
INSERT INTO business_hours (day_of_week, start_time, end_time) VALUES
    (1, '09:00', '18:00'),
    (2, '09:00', '18:00'),
    (3, '09:00', '18:00'),
    (4, '09:00', '18:00'),
    (5, '09:00', '18:00')
ON CONFLICT DO NOTHING;

-- Starter SLA policy per existing seeded category (V7) — response/resolution
-- windows are placeholder defaults, tune per category later. Security gets
-- the tightest window given the CRITICAL priority rules already seeded for it.
INSERT INTO sla_policies (category_id, response_hours, resolution_hours)
SELECT id, 2, 8 FROM ticket_categories WHERE name = 'Security'
ON CONFLICT DO NOTHING;

INSERT INTO sla_policies (category_id, response_hours, resolution_hours)
SELECT id, 4, 24 FROM ticket_categories WHERE name IN ('Laptop Issue', 'Network Problem')
ON CONFLICT DO NOTHING;

INSERT INTO sla_policies (category_id, response_hours, resolution_hours)
SELECT id, 8, 48 FROM ticket_categories WHERE name IN ('Software Request', 'Printer Issue')
ON CONFLICT DO NOTHING;

-- SLA_BREACH_WARNING was defined-but-unused in notification-service's
-- Notification.type enum since 5.4 — no schema change needed there, this is
-- just the moment it starts actually firing.
