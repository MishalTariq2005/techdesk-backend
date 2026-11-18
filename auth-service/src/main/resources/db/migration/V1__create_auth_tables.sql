-- auth-service owns this migration set. Runs against the "auth" schema
-- (see application.yml: spring.flyway.schemas=auth) — kept separate from
-- tenant-service's public schema so the two services' Flyway histories
-- never collide on the same Postgres instance.

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    role VARCHAR(50) NOT NULL,
    -- comma-separated for now; revisit as jsonb once the permission model
    -- firms up in Week 2 (RBAC). Good enough to unblock JWT claims today.
    permissions VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    -- SHA-256 hash of the raw token handed to the client. The raw value
    -- is never persisted, so a DB leak alone can't be used to authenticate.
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    -- Set when this token is rotated to its successor. If a token whose
    -- chain already has a replacement gets presented again, that's a replay.
    replaced_by_token_id UUID REFERENCES refresh_tokens(id)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);