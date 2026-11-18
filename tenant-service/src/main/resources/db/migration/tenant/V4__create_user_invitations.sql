-- V4: user invitation flow support
CREATE TABLE user_invitations (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email        VARCHAR(255) NOT NULL,
    role         VARCHAR(50) NOT NULL,
    department   VARCHAR(100),
    token_hash   VARCHAR(64) NOT NULL UNIQUE, -- SHA-256 hex, same pattern as refresh tokens
    invited_by   UUID NOT NULL REFERENCES users(id),
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | ACCEPTED | EXPIRED | REVOKED
    expires_at   TIMESTAMP NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    accepted_at  TIMESTAMP
);

CREATE INDEX idx_user_invitations_email ON user_invitations(email);
CREATE UNIQUE INDEX idx_user_invitations_pending_email
    ON user_invitations(email) WHERE status = 'PENDING';
