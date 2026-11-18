-- V2: RBAC schema, scoped inside each tenant schema (runs via SchemaProvisioningService,
-- same as tenant/V1). SUPER_ADMIN is intentionally NOT seeded here — it is a platform-level
-- role that only ever appears as a JWT claim minted outside tenant context (see open question
-- in Week 1 handoff notes). Seeding it into every tenant schema would let a tenant provision
-- its own super-admin, which breaks the isolation model.

CREATE TABLE roles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(50) NOT NULL UNIQUE,
    description     VARCHAR(255),
    is_system_role  BOOLEAN NOT NULL DEFAULT TRUE, -- seeded roles can't be deleted via API
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE permissions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(100) NOT NULL UNIQUE, -- e.g. TICKET_VIEW_ALL
    description VARCHAR(255)
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- Seed permissions
INSERT INTO permissions (code, description) VALUES
    ('USER_INVITE',       'Invite a new user into the company'),
    ('USER_MANAGE',       'Edit/deactivate users, change roles'),
    ('USER_VIEW',         'View user list and profiles'),
    ('ROLE_MANAGE',       'Modify role-permission assignments'),
    ('TICKET_CREATE',     'Create a support ticket'),
    ('TICKET_VIEW_OWN',   'View tickets created by or assigned to self'),
    ('TICKET_VIEW_ALL',   'View every ticket in the tenant'),
    ('TICKET_ASSIGN',     'Reassign tickets between IT staff'),
    ('TICKET_RESOLVE',    'Change ticket status / resolve tickets'),
    ('ASSET_MANAGE',      'Create/update/assign assets'),
    ('GADGET_APPROVE',    'Approve or reject gadget requests'),
    ('REPORT_VIEW',       'View reporting dashboards'),
    ('AUDIT_VIEW',        'View audit logs')
ON CONFLICT DO NOTHING;

-- Seed roles
INSERT INTO roles (name, description) VALUES
    ('COMPANY_ADMIN', 'Full control over this tenant'),
    ('IT_MANAGER',    'Oversees IT staff, approvals, SLAs'),
    ('IT_STAFF',      'Resolves assigned tickets'),
    ('EMPLOYEE',      'Raises tickets and gadget requests'),
    ('AUDITOR',       'Read-only compliance access')
ON CONFLICT DO NOTHING;

-- Wire role -> permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'COMPANY_ADMIN'
  AND p.code IN ('USER_INVITE','USER_MANAGE','USER_VIEW','ROLE_MANAGE',
                 'TICKET_VIEW_ALL','TICKET_ASSIGN','ASSET_MANAGE',
                 'GADGET_APPROVE','REPORT_VIEW','AUDIT_VIEW');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'IT_MANAGER'
  AND p.code IN ('USER_VIEW','TICKET_VIEW_ALL','TICKET_ASSIGN','TICKET_RESOLVE',
                 'ASSET_MANAGE','GADGET_APPROVE','REPORT_VIEW','AUDIT_VIEW');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'IT_STAFF'
  AND p.code IN ('TICKET_VIEW_OWN','TICKET_RESOLVE','ASSET_MANAGE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'EMPLOYEE'
  AND p.code IN ('TICKET_CREATE','TICKET_VIEW_OWN');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'AUDITOR'
  AND p.code IN ('AUDIT_VIEW','REPORT_VIEW');
