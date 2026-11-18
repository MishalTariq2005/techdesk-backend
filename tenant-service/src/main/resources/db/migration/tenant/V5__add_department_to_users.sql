-- V5: your 3.3 tenant/V1 users table never got a department column
-- (only first_name, last_name, status, must_change_password, permissions).
-- The roadmap's GET /api/users filter list requires it.
ALTER TABLE users ADD COLUMN department VARCHAR(100);
