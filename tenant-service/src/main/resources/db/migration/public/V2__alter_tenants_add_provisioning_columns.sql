-- Your tenants table already exists from 3.1 with: id, name, schema_name,
-- status, created_at. This adds what 3.3's provisioning flow needs on top
-- of that, without touching the existing columns.
--
-- RENAME THIS FILE if V2 isn't actually the next free version number in
-- your migration directory — check what's already there first.

ALTER TABLE tenants
    ADD COLUMN admin_email VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN failure_reason TEXT,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT now();

-- Existing rows (if any) get a blank admin_email to satisfy NOT NULL; new
-- rows must always supply a real one going forward.
ALTER TABLE tenants
    ALTER COLUMN admin_email DROP DEFAULT;

-- schema_name already has its own UNIQUE constraint from 3.1. This adds the
-- same guarantee for company name, so two tenants can't collide on it even
-- under concurrent requests racing past the application-level check.
ALTER TABLE tenants
    ADD CONSTRAINT uq_tenants_name UNIQUE (name);

CREATE INDEX IF NOT EXISTS idx_tenants_status ON tenants (status);
