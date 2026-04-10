ALTER TABLE IF EXISTS recon.users
    ADD COLUMN IF NOT EXISTS failed_login_attempts integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until timestamp,
    ADD COLUMN IF NOT EXISTS last_failed_login_at timestamp;

ALTER TABLE IF EXISTS recon.tenant_api_keys
    ADD COLUMN IF NOT EXISTS expires_at timestamp,
    ADD COLUMN IF NOT EXISTS revoked_at timestamp;

CREATE INDEX IF NOT EXISTS idx_users_tenant_locked_until
    ON recon.users (tenant_id, locked_until)
    WHERE locked_until IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tenant_api_keys_expiry
    ON recon.tenant_api_keys (tenant_id, active, expires_at);
