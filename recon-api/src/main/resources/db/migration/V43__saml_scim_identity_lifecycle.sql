ALTER TABLE IF EXISTS recon.users
    ADD COLUMN IF NOT EXISTS directory_external_id varchar(300);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_tenant_directory_external_id_unique
    ON recon.users (tenant_id, lower(directory_external_id))
    WHERE directory_external_id IS NOT NULL;

ALTER TABLE IF EXISTS recon.tenant_auth_config
    ADD COLUMN IF NOT EXISTS saml_acs_url text,
    ADD COLUMN IF NOT EXISTS saml_idp_entity_id varchar(255),
    ADD COLUMN IF NOT EXISTS saml_idp_metadata_url text,
    ADD COLUMN IF NOT EXISTS saml_idp_verification_certificate text,
    ADD COLUMN IF NOT EXISTS saml_username_attribute varchar(120) NOT NULL DEFAULT 'uid',
    ADD COLUMN IF NOT EXISTS scim_enabled boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS scim_bearer_token_ref varchar(160);

CREATE TABLE IF NOT EXISTS recon.tenant_saml_login_states (
    relay_state_hash varchar(96) PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    request_id varchar(120) NOT NULL,
    expires_at timestamp NOT NULL,
    consumed_at timestamp,
    created_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tenant_saml_login_states_expiry
    ON recon.tenant_saml_login_states (expires_at);

CREATE TABLE IF NOT EXISTS recon.tenant_sso_completion_codes (
    code_hash varchar(96) PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    user_id uuid NOT NULL REFERENCES recon.users(id) ON DELETE CASCADE,
    auth_mode varchar(40) NOT NULL,
    expires_at timestamp NOT NULL,
    consumed_at timestamp,
    created_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tenant_sso_completion_codes_expiry
    ON recon.tenant_sso_completion_codes (expires_at);
