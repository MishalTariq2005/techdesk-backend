-- V7: 4.2 — ticket categories and the priority engine's rule table.
-- Owned by tenant-service (same reasoning as V1-V6: tenant-service runs
-- Flyway against tenant schemas), even though ticket-service is the one
-- reading/writing these tables at runtime.

CREATE TABLE ticket_categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE priority_rules (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id         UUID REFERENCES ticket_categories(id) ON DELETE CASCADE, -- null = any category
    keyword             VARCHAR(255),          -- null = don't check description
    reporter_department VARCHAR(100),          -- null = any department
    priority            VARCHAR(20) NOT NULL CHECK (priority IN ('CRITICAL','HIGH','MEDIUM','LOW')),
    weight              INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_priority_rules_category ON priority_rules(category_id);
CREATE INDEX idx_priority_rules_weight ON priority_rules(weight DESC);

-- New permission for ticket-category/priority-rule configuration —
-- distinct from ROLE_MANAGE (RBAC role/permission assignment) since this
-- is tenant ticket-config, a different concern.
INSERT INTO permissions (code, description) VALUES
    ('TICKET_CATEGORY_MANAGE', 'Create/edit/delete ticket categories and priority rules')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'COMPANY_ADMIN' AND p.code = 'TICKET_CATEGORY_MANAGE'
ON CONFLICT DO NOTHING;

-- Seed a starter set matching the categories your Project Understanding
-- doc's scenarios reference (Laptop Issue, Internet Problem, etc.) — delete/
-- edit freely, this is just so priority-preview has something to test against.
INSERT INTO ticket_categories (name, description) VALUES
    ('Laptop Issue', 'Hardware or performance problems with a laptop'),
    ('Network Problem', 'VPN, WiFi, or connectivity issues'),
    ('Software Request', 'Installing or licensing software'),
    ('Security', 'Suspected breaches, phishing, unauthorized access'),
    ('Printer Issue', 'Printer/scanner hardware problems')
ON CONFLICT DO NOTHING;

-- Example compound rule matching the roadmap's own example:
-- category = Security AND keyword contains 'breach' AND department = 'Finance' -> CRITICAL
INSERT INTO priority_rules (category_id, keyword, reporter_department, priority, weight)
SELECT id, 'breach', 'Finance', 'CRITICAL', 100 FROM ticket_categories WHERE name = 'Security';

-- Broader fallback: any Security-category ticket mentioning "breach", any department -> HIGH
INSERT INTO priority_rules (category_id, keyword, reporter_department, priority, weight)
SELECT id, 'breach', NULL, 'HIGH', 50 FROM ticket_categories WHERE name = 'Security';

-- Broadest: any Security-category ticket at all -> MEDIUM (baseline, low weight)
INSERT INTO priority_rules (category_id, keyword, reporter_department, priority, weight)
SELECT id, NULL, NULL, 'MEDIUM', 10 FROM ticket_categories WHERE name = 'Security';
