ALTER TABLE IF EXISTS recon.users
    ADD COLUMN IF NOT EXISTS manager_user_id uuid;

CREATE INDEX IF NOT EXISTS idx_users_tenant_manager
    ON recon.users (tenant_id, manager_user_id);

CREATE TABLE IF NOT EXISTS recon.user_emergency_access_grants (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    user_id uuid NOT NULL REFERENCES recon.users(id) ON DELETE CASCADE,
    justification text NOT NULL,
    approval_note text NOT NULL,
    granted_by varchar(120) NOT NULL,
    granted_at timestamp NOT NULL DEFAULT now(),
    expires_at timestamp NOT NULL,
    revoked_at timestamp,
    revoked_by varchar(120),
    revoke_note text,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_user_emergency_access_grants_active
    ON recon.user_emergency_access_grants (tenant_id, user_id, revoked_at, expires_at);

CREATE INDEX IF NOT EXISTS idx_user_emergency_access_grants_tenant_granted
    ON recon.user_emergency_access_grants (tenant_id, granted_at DESC);

CREATE TABLE IF NOT EXISTS recon.user_emergency_access_grant_roles (
    grant_id uuid NOT NULL REFERENCES recon.user_emergency_access_grants(id) ON DELETE CASCADE,
    role_id uuid NOT NULL REFERENCES recon.roles(id) ON DELETE CASCADE,
    PRIMARY KEY (grant_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_user_emergency_access_grant_roles_role
    ON recon.user_emergency_access_grant_roles (role_id);
