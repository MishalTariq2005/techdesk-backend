-- Takes over the `audit_logs` table tenant-service's public/V1 migration created
-- back in Week 1. Per the Week 3 handoff (open item #9), nothing has ever
-- written to it — priority-rule conflicts and status-change logging both
-- still go through SLF4J only. Confirmed empty, so this migration is free to
-- DROP + recreate with the correct shape rather than fight with fragile
-- ALTER COLUMN casts, most importantly fixing a real bug in the original
-- definition: actor_id was BIGINT, but every actor in this system (User.id)
-- is a UUID — that column could never have held a real value.
--
-- entity_id is VARCHAR rather than a single numeric/UUID type because the
-- entities being audited span multiple services with different ID schemes
-- (UUID for users/tickets/assets/gadget-requests, BIGINT for tenants) — a
-- single audit table has to be able to record any of them.
DROP TABLE IF EXISTS audit_logs;

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    UUID,
    tenant_id   BIGINT REFERENCES tenants(id),
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id   VARCHAR(100),
    old_value   JSONB,
    new_value   JSONB,
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(255),
    -- Base64 HMAC-SHA256 signature over the row's other fields, computed at
    -- write time with a server secret (app.audit.hmac-secret). Nullable only
    -- because a handful of legacy/manual inserts could theoretically lack
    -- one; every row written through this service always has one.
    signature   VARCHAR(88),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_tenant_id   ON audit_logs(tenant_id);
CREATE INDEX idx_audit_logs_actor_id    ON audit_logs(actor_id);
CREATE INDEX idx_audit_logs_entity_type ON audit_logs(entity_type);
CREATE INDEX idx_audit_logs_created_at  ON audit_logs(created_at);

-- Immutability: no UPDATE or DELETE, ever — enforced at the DB level so it
-- can't be bypassed by application-layer bugs (or a superuser connection
-- like techdesk_admin, which is exactly the kind of bypass RLS already
-- suffers from per the Week 1-2 open items — a trigger can't be skipped
-- the way a superuser skips RLS).
CREATE OR REPLACE FUNCTION reject_audit_log_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only: % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_no_update
    BEFORE UPDATE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION reject_audit_log_mutation();

CREATE TRIGGER trg_audit_logs_no_delete
    BEFORE DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION reject_audit_log_mutation();
