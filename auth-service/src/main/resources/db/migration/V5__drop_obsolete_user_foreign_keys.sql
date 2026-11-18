ALTER TABLE auth.refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_user_id_fkey;
ALTER TABLE auth.password_reset_tokens DROP CONSTRAINT IF EXISTS password_reset_tokens_user_id_fkey;