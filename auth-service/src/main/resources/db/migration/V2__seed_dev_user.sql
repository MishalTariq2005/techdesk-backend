-- Dev-only seed user. tenant_id intentionally matches the "acme-corp"
-- subdomain you already verified against the gateway filter, so you can
-- test the full pipeline (subdomain -> X-Tenant-ID -> login) end-to-end.
--
-- Credentials: admin@acme.test / password123
-- Remove or replace this migration before this goes anywhere beyond local dev.
 
INSERT INTO users (email, password_hash, tenant_id, role, permissions)
VALUES (
    'admin@acme.test',
    '$2b$10$wEHmanS61hAJCmSiGG1bEeJGQMRnyChcDh.UQz86upuHks63UMh7a',
    'acme-corp',
    'COMPANY_ADMIN',
    'users:read,users:write,tickets:read,tickets:write'
);
 