-- Minimal local event log for auth-service security incidents (e.g. refresh
-- token reuse / replay detection). This is intentionally NOT the same as
-- tenant-service's audit_logs table - wiring auth-service into a shared,
-- properly event-driven audit trail is Week 4's Audit Log Service (6.3).
-- This table exists so "security event logged on reuse" is a real, queryable
-- record right now rather than a log line that scrolls away.

CREATE TABLE security_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_security_events_user_id ON security_events(user_id);