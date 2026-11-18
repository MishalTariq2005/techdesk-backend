-- V8: 4.3 — ticket CRUD & lifecycle tables. Owned by tenant-service per
-- the established convention (V1-V7), even though ticket-service is the
-- runtime owner of these tables.

CREATE TABLE tickets (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id          UUID NOT NULL REFERENCES ticket_categories(id),
    title                VARCHAR(255) NOT NULL,
    description          TEXT NOT NULL,
    status               VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    priority             VARCHAR(20) NOT NULL,
    created_by           UUID NOT NULL,
    reporter_department  VARCHAR(100),
    assigned_to          UUID, -- null until 4.4's auto-assignment engine exists
    version              BIGINT NOT NULL DEFAULT 0, -- backs @Version / optimistic locking
    created_at           TIMESTAMP NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_tickets_status ON tickets(status);
CREATE INDEX idx_tickets_priority ON tickets(priority);
CREATE INDEX idx_tickets_category ON tickets(category_id);
CREATE INDEX idx_tickets_assigned_to ON tickets(assigned_to);
CREATE INDEX idx_tickets_created_by ON tickets(created_by);

CREATE TABLE ticket_comments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id   UUID NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    author_id   UUID NOT NULL,
    content     TEXT NOT NULL,
    internal    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_ticket_comments_ticket ON ticket_comments(ticket_id);

CREATE TABLE ticket_assignment_logs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id    UUID NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    actor_id     UUID, -- null = system-initiated
    from_status  VARCHAR(30),
    to_status    VARCHAR(30),
    assigned_to  UUID, -- populated for assignment events (4.4), null for pure status changes
    reason       VARCHAR(500),
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_ticket_assignment_logs_ticket ON ticket_assignment_logs(ticket_id);
