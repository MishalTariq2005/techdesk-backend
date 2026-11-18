-- V3: Row-Level Security — the 3rd of the 3 RBAC enforcement layers.
-- Session GUCs are set per-connection by SchemaMultiTenantConnectionProvider
-- (see RoleContext / auth-service patch notes). Two GUCs are used:
--   techdesk.current_role  -> the caller's role name
--   techdesk.current_user_id -> the caller's user id (for "own row" style policies)
--
-- NOTE: techdesk_admin (the app's DB user) still has BYPASSRLS-equivalent access
-- unless explicitly restricted, because table owners bypass RLS by default in
-- Postgres. FORCE ROW LEVEL SECURITY closes that gap for the owning role too.

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;

-- COMPANY_ADMIN / IT_MANAGER / AUDITOR can see every user row in the schema.
CREATE POLICY users_select_privileged ON users
    FOR SELECT
    USING (
        current_setting('techdesk.current_role', true) IN ('COMPANY_ADMIN', 'IT_MANAGER', 'AUDITOR')
    );

-- Everyone else (IT_STAFF, EMPLOYEE) can only see their own row.
CREATE POLICY users_select_self ON users
    FOR SELECT
    USING (
        id::text = current_setting('techdesk.current_user_id', true)
    );

-- Only COMPANY_ADMIN can INSERT/UPDATE/DELETE user rows directly at the DB level.
-- (Application-level checks via @RequiresPermission are the primary gate; this is
-- the last line of defense if a query bypasses the service layer.)
CREATE POLICY users_write_admin_only ON users
    FOR ALL
    USING (current_setting('techdesk.current_role', true) = 'COMPANY_ADMIN')
    WITH CHECK (current_setting('techdesk.current_role', true) = 'COMPANY_ADMIN');

-- A user must always be able to update their own row for the "set password on
-- invite accept" flow — carve out an explicit exception.
CREATE POLICY users_update_self ON users
    FOR UPDATE
    USING (id::text = current_setting('techdesk.current_user_id', true))
    WITH CHECK (id::text = current_setting('techdesk.current_user_id', true));
