-- Runs against EACH tenant schema individually (Flyway is invoked once per
-- tenant, scoped via .schemas(schemaName) in SchemaProvisioningService).
-- No schema prefix needed here — Flyway sets the search_path for the
-- migration session to the target tenant schema.
CREATE TABLE users (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                 VARCHAR(255) NOT NULL UNIQUE,
    password_hash         VARCHAR(255) NOT NULL,
    first_name            VARCHAR(100) NOT NULL,
    last_name             VARCHAR(100) NOT NULL,
    role                  VARCHAR(50)  NOT NULL DEFAULT 'COMPANY_ADMIN',
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    must_change_password  BOOLEAN      NOT NULL DEFAULT TRUE,
    permissions           TEXT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);