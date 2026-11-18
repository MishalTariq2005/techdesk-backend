-- V10: 5.1 — Asset Management (asset-service, NEW service this project).
-- Lives here, not in asset-service's own repo — tenant-service is the sole
-- owner of all tenant-schema Flyway migrations (see the environment note:
-- "tenant-service owns ALL tenant-schema Flyway migrations"). asset-service
-- has flyway.enabled: false, identical to ticket-service's config. Same
-- convention already followed for V7/V8/V9 (ticket_categories, tickets,
-- staff_profiles) even though those tables are conceptually "owned" by
-- ticket-service's code.
--
-- REMINDER (same as every migration since V8): a file existing here does
-- NOT mean it's applied to already-provisioned tenants. Run manually via
-- docker exec ... psql against tenant_acme_corp / tenant_globex_inc after
-- deploying — brand-new tenants get the full current migration set
-- automatically at creation time via SchemaProvisioningService.

CREATE TABLE assets (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    serial_number         VARCHAR(100) NOT NULL,
    name                  VARCHAR(200) NOT NULL,
    asset_type            VARCHAR(100),
    status                VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    assigned_to           UUID,              -- no FK: users live in auth-service's schema, different service
    assigned_at           TIMESTAMP,
    purchase_date         DATE,
    warranty_expiry_date  DATE,
    notes                 VARCHAR(500),
    deleted_at            TIMESTAMP,         -- soft delete; NULL = active
    created_at            TIMESTAMP NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP NOT NULL DEFAULT now(),
    version               BIGINT NOT NULL DEFAULT 0   -- @Version — optimistic locking on concurrent /assign races,
                                                        -- same mechanism as tickets.version from 4.3
);

-- ASSUMPTION, flagged per project convention: serial numbers are unique
-- within the tenant regardless of soft-delete state — a deleted asset's
-- serial number stays "spoken for" rather than becoming reusable. Reissuing
-- a serial to a different physical asset after a soft-delete risks
-- corrupting assignment history. Revisit if that turns out to be wrong.
CREATE UNIQUE INDEX idx_assets_serial_number ON assets (LOWER(serial_number));

CREATE INDEX idx_assets_status ON assets(status);
CREATE INDEX idx_assets_assigned_to ON assets(assigned_to);
CREATE INDEX idx_assets_warranty_expiry ON assets(warranty_expiry_date) WHERE deleted_at IS NULL;

CREATE TABLE asset_assignment_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id        UUID NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    employee_id     UUID NOT NULL,     -- no FK, same cross-service reasoning as assets.assigned_to
    event_type      VARCHAR(20) NOT NULL,   -- 'ASSIGNED' | 'RETURNED'
    event_timestamp TIMESTAMP NOT NULL DEFAULT now(),
    actor_user_id   UUID NOT NULL,     -- who performed the assign/return action
    notes           VARCHAR(500),
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_asset_history_asset ON asset_assignment_history(asset_id);

-- New permission codes for asset-service, seeded the same way V7 seeded
-- TICKET_CATEGORY_MANAGE. Single ASSET_MANAGE permission covers CRUD +
-- assign/return for now (see AssetController's doc comment — reads aren't
-- split from writes the way ticket-category's are; assigned-employee data
-- is sensitive enough to gate reads too, as a default).
INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'ASSET_MANAGE', 'View, create, update, assign, and return assets')
ON CONFLICT (code) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'COMPANY_ADMIN' AND p.code = 'ASSET_MANAGE'
ON CONFLICT DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'IT_MANAGER' AND p.code = 'ASSET_MANAGE'
ON CONFLICT DO NOTHING;
