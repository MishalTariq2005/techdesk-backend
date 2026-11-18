-- V6: techdesk.current_role collided with Postgres's reserved keyword
-- CURRENT_ROLE (a builtin pseudo-function, like CURRENT_USER). SET/RESET
-- on that name fail with a syntax error. Renamed to techdesk.user_role
-- everywhere — this migration drops and recreates the affected policies
-- from tenant/V3 to match.

DROP POLICY IF EXISTS users_select_privileged ON users;
DROP POLICY IF EXISTS users_select_self ON users;
DROP POLICY IF EXISTS users_write_admin_only ON users;
DROP POLICY IF EXISTS users_update_self ON users;

CREATE POLICY users_select_privileged ON users
    FOR SELECT
    USING (
        current_setting('techdesk.user_role', true) IN ('COMPANY_ADMIN', 'IT_MANAGER', 'AUDITOR')
    );

CREATE POLICY users_select_self ON users
    FOR SELECT
    USING (
        id::text = current_setting('techdesk.current_user_id', true)
    );

CREATE POLICY users_write_admin_only ON users
    FOR ALL
    USING (current_setting('techdesk.user_role', true) = 'COMPANY_ADMIN')
    WITH CHECK (current_setting('techdesk.user_role', true) = 'COMPANY_ADMIN');

CREATE POLICY users_update_self ON users
    FOR UPDATE
    USING (id::text = current_setting('techdesk.current_user_id', true))
    WITH CHECK (id::text = current_setting('techdesk.current_user_id', true));
