-- Seed data for tenant_demo. Run with search_path already set to the
-- target schema (see seed-demo-tenant.sh). Idempotent-ish via ON CONFLICT
-- guards on unique columns (email) — re-running won't duplicate users, but
-- WILL add another batch of 20 tickets each time (tickets have no natural
-- unique key to conflict on). Fine for a demo seed script; not written to
-- be run repeatedly against a schema you care about the ticket count in.

-- All seeded users share the password: Demo1234!
-- (bcrypt cost-12 hash, matching auth-service's 6.4-hardened PasswordEncoder)

-- 1 Company Admin
INSERT INTO users (email, password_hash, first_name, last_name, role, department, must_change_password, permissions)
SELECT
    'admin@demo.techdesk.local',
    '$2b$12$8LwjyeZQYz.viBH80xSZz.KGL7TQ2Se1UD2I0CmhIc3o8y6T0CgXG',
    'Demo', 'Admin', 'COMPANY_ADMIN', 'Operations', false,
    (SELECT string_agg(p.code, ',') FROM role_permissions rp
        JOIN permissions p ON p.id = rp.permission_id
        JOIN roles r ON r.id = rp.role_id WHERE r.name = 'COMPANY_ADMIN')
ON CONFLICT (email) DO NOTHING;

-- 1 IT Manager
INSERT INTO users (email, password_hash, first_name, last_name, role, department, must_change_password, permissions)
SELECT
    'manager@demo.techdesk.local',
    '$2b$12$8LwjyeZQYz.viBH80xSZz.KGL7TQ2Se1UD2I0CmhIc3o8y6T0CgXG',
    'Demo', 'Manager', 'IT_MANAGER', 'IT', false,
    (SELECT string_agg(p.code, ',') FROM role_permissions rp
        JOIN permissions p ON p.id = rp.permission_id
        JOIN roles r ON r.id = rp.role_id WHERE r.name = 'IT_MANAGER')
ON CONFLICT (email) DO NOTHING;

-- 3 IT Staff
INSERT INTO users (email, password_hash, first_name, last_name, role, department, must_change_password, permissions)
SELECT
    'itstaff' || n || '@demo.techdesk.local',
    '$2b$12$8LwjyeZQYz.viBH80xSZz.KGL7TQ2Se1UD2I0CmhIc3o8y6T0CgXG',
    'IT Staff', 'Number ' || n, 'IT_STAFF', 'IT', false,
    (SELECT string_agg(p.code, ',') FROM role_permissions rp
        JOIN permissions p ON p.id = rp.permission_id
        JOIN roles r ON r.id = rp.role_id WHERE r.name = 'IT_STAFF')
FROM generate_series(1, 3) AS n
ON CONFLICT (email) DO NOTHING;

-- 10 Employees, spread across a few departments for variety in
-- reporter_department-based priority rules and category-breakdown reports.
INSERT INTO users (email, password_hash, first_name, last_name, role, department, must_change_password, permissions)
SELECT
    'employee' || n || '@demo.techdesk.local',
    '$2b$12$8LwjyeZQYz.viBH80xSZz.KGL7TQ2Se1UD2I0CmhIc3o8y6T0CgXG',
    'Employee', 'Number ' || n, 'EMPLOYEE',
    (ARRAY['Finance','Engineering','Sales','HR','Operations'])[1 + (n % 5)],
    false,
    (SELECT string_agg(p.code, ',') FROM role_permissions rp
        JOIN permissions p ON p.id = rp.permission_id
        JOIN roles r ON r.id = rp.role_id WHERE r.name = 'EMPLOYEE')
FROM generate_series(1, 10) AS n
ON CONFLICT (email) DO NOTHING;

-- Staff profiles + skills for the 3 IT staff (roadmap: "IT staff skills are
-- stored in a staff_skills junction table — seeded during user creation" —
-- 4.4's own migration notes document this as a deliberate manual-setup
-- decision instead, no auto-sync exists; this seed script is exactly the
-- kind of manual setup that note anticipated).
INSERT INTO staff_profiles (staff_user_id, available, location, max_active_tickets)
SELECT id, true, department, 15 FROM users WHERE role = 'IT_STAFF'
ON CONFLICT (staff_user_id) DO NOTHING;

INSERT INTO staff_skills (staff_user_id, category_id, skill_level)
SELECT u.id, c.id, 1 + floor(random() * 5)::int
FROM users u CROSS JOIN ticket_categories c
WHERE u.role = 'IT_STAFF'
ON CONFLICT (staff_user_id, category_id) DO NOTHING;

-- 20 tickets in various states, spread across categories/priorities, with
-- SLA due dates computed from whatever sla_policies exist for each
-- category (V14 seed data), and roughly 1-in-7 marked as breached for
-- variety in the SLA dashboard / reporting-service demo data.
INSERT INTO tickets (category_id, title, description, status, priority, created_by,
                      reporter_department, assigned_to, created_at, updated_at,
                      sla_due_at, sla_breached)
SELECT
    c.id,
    'Demo ticket #' || n || ': ' || st.status_val || ' / ' || pr.priority_val,
    'Auto-generated demo ticket seeded for local development and testing.',
    st.status_val,
    pr.priority_val,
    e.id,
    e.department,
    CASE WHEN st.status_val <> 'OPEN' THEN s.id ELSE NULL END,
    now() - ((n * 3) || ' hours')::interval,
    now() - ((n * 3) || ' hours')::interval,
    CASE WHEN sp.resolution_hours IS NOT NULL
         THEN now() - ((n * 3) || ' hours')::interval + (sp.resolution_hours || ' hours')::interval
         ELSE NULL END,
    (n % 7 = 0)
FROM generate_series(1, 20) AS n
JOIN LATERAL (SELECT id FROM ticket_categories ORDER BY random() LIMIT 1) c ON true
JOIN LATERAL (SELECT (ARRAY['OPEN','AUTO_ASSIGNED','IN_PROGRESS','RESOLVED','CLOSED','ESCALATED','CANCELLED'])[1 + (n % 7)] AS status_val) st ON true
JOIN LATERAL (SELECT (ARRAY['LOW','MEDIUM','HIGH','CRITICAL'])[1 + (n % 4)] AS priority_val) pr ON true
JOIN LATERAL (SELECT id, department FROM users WHERE role = 'EMPLOYEE' ORDER BY random() LIMIT 1) e ON true
LEFT JOIN LATERAL (SELECT id FROM users WHERE role = 'IT_STAFF' ORDER BY random() LIMIT 1) s ON true
LEFT JOIN sla_policies sp ON sp.category_id = c.id;
