-- V11: 5.2 — Gadget Request Workflow (lives inside asset-service, NOT a
-- new service — see the explicit "where does this code live" decision:
-- approval needs to check-and-reserve an Asset atomically, so this needed
-- to be in the same schema/transaction boundary as Asset itself).
--
-- Same convention as every migration since V7: this file lives in
-- tenant-service's migration set even though the code it supports lives in
-- asset-service. Also same reminder as always: existing tenants
-- (tenant_acme_corp, tenant_globex_inc, tenant_nova_technologies) need this
-- run manually via docker exec ... psql; only brand-new tenants get it
-- automatically via SchemaProvisioningService.

CREATE TABLE gadget_types (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(200) NOT NULL,
    description       VARCHAR(500),
    requires_approval BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_gadget_types_name ON gadget_types (LOWER(name));

CREATE TABLE gadget_requests (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gadget_type_id    UUID NOT NULL REFERENCES gadget_types(id),
    employee_id       UUID NOT NULL,   -- no FK: users live in auth-service's schema, different service
    reason            VARCHAR(1000),
    status            VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    assigned_asset_id UUID,            -- no FK to assets(id) deliberately: an asset can be soft-deleted
                                        -- later without corrupting this historical reference
    rejection_reason  VARCHAR(500),
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0   -- @Version — same optimistic-locking reasoning as
                                                    -- assets.version (5.1) and tickets.version (4.3)
);

CREATE INDEX idx_gadget_requests_status ON gadget_requests(status);
CREATE INDEX idx_gadget_requests_employee ON gadget_requests(employee_id);
CREATE INDEX idx_gadget_requests_type ON gadget_requests(gadget_type_id);

-- The audit-replay backbone for 5.2's challenge. Append-only by convention
-- (not enforced by a DB trigger the way audit_logs will be in 6.3 — that's
-- a real gap vs. that table's eventual immutability guarantee, noted here
-- rather than silently matched, since this table predates 6.3's trigger
-- pattern and nothing currently stops an UPDATE/DELETE against it).
CREATE TABLE gadget_request_decision_log (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gadget_request_id UUID NOT NULL REFERENCES gadget_requests(id) ON DELETE CASCADE,
    from_status       VARCHAR(30),     -- NULL for the very first REQUESTED row
    to_status         VARCHAR(30) NOT NULL,
    actor_user_id     UUID,            -- NULL = system-triggered, not a human decision (meaningful, not a gap)
    reason            VARCHAR(500),
    asset_id          UUID,            -- populated only on the transition that actually reserves an asset
    event_timestamp   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_gadget_decision_log_request ON gadget_request_decision_log(gadget_request_id);

-- New permission: distinct from GADGET_APPROVE (which already existed since
-- V2, for approving/rejecting a SPECIFIC request). This one gates
-- configuring the catalog itself — same tier as TICKET_CATEGORY_MANAGE.
INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'GADGET_TYPE_MANAGE', 'Create, update, and delete gadget types')
ON CONFLICT (code) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'COMPANY_ADMIN' AND p.code = 'GADGET_TYPE_MANAGE'
ON CONFLICT DO NOTHING;
-- Seed data purely to make manual testing possible without an extra setup
-- call first — same spirit as V7 seeding 5 categories + 3 example rules.
-- One type per approval branch, so both the auto-approval and
-- manager-approval code paths are reachable immediately.
INSERT INTO gadget_types (id, name, description, requires_approval) VALUES
    (gen_random_uuid(), 'Laptop', 'Standard issue laptop', TRUE),
    (gen_random_uuid(), 'Wireless Mouse', 'Standard wireless mouse', FALSE);
