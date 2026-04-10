ALTER TABLE IF EXISTS recon.tenant_auth_config
    ADD COLUMN IF NOT EXISTS oidc_redirect_uri text,
    ADD COLUMN IF NOT EXISTS oidc_scopes varchar(300) NOT NULL DEFAULT 'openid profile email',
    ADD COLUMN IF NOT EXISTS oidc_client_secret_ref varchar(160);

CREATE TABLE IF NOT EXISTS recon.tenant_oidc_login_states (
    state_hash varchar(96) PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    redirect_uri text NOT NULL,
    code_verifier varchar(160) NOT NULL,
    nonce varchar(96) NOT NULL,
    expires_at timestamp NOT NULL,
    consumed_at timestamp,
    created_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tenant_oidc_login_states_expiry
    ON recon.tenant_oidc_login_states (expires_at);

CREATE TABLE IF NOT EXISTS recon.tenant_oidc_group_role_mappings (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    oidc_group varchar(300) NOT NULL,
    role_id uuid NOT NULL REFERENCES recon.roles(id) ON DELETE CASCADE,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    updated_by varchar(100)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_tenant_oidc_group_role_unique
    ON recon.tenant_oidc_group_role_mappings (tenant_id, lower(oidc_group), role_id);

CREATE INDEX IF NOT EXISTS idx_tenant_oidc_group_role_tenant
    ON recon.tenant_oidc_group_role_mappings (tenant_id, is_active);
