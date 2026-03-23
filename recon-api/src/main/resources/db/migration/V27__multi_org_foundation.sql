ALTER TABLE IF EXISTS recon.users
    DROP CONSTRAINT IF EXISTS users_username_key,
    DROP CONSTRAINT IF EXISTS users_email_key;

DROP INDEX IF EXISTS recon.idx_users_username_unique;
DROP INDEX IF EXISTS recon.idx_users_email_unique;

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_tenant_username_unique
    ON recon.users (tenant_id, lower(username));

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_tenant_email_unique
    ON recon.users (tenant_id, lower(email));

CREATE TABLE IF NOT EXISTS recon.organization_units (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    unit_key varchar(120) NOT NULL,
    unit_name varchar(200) NOT NULL,
    unit_type varchar(30) NOT NULL,
    parent_unit_id uuid NULL REFERENCES recon.organization_units(id) ON DELETE CASCADE,
    store_id varchar(50),
    sort_order integer NOT NULL DEFAULT 0,
    active boolean NOT NULL DEFAULT true,
    created_by varchar(100),
    updated_by varchar(100),
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT uk_organization_units_tenant_key UNIQUE (tenant_id, unit_key)
);

CREATE INDEX IF NOT EXISTS idx_organization_units_tenant_parent
    ON recon.organization_units (tenant_id, parent_unit_id, sort_order, unit_name);

CREATE INDEX IF NOT EXISTS idx_organization_units_tenant_type
    ON recon.organization_units (tenant_id, unit_type, active, unit_name);

CREATE INDEX IF NOT EXISTS idx_organization_units_tenant_store
    ON recon.organization_units (tenant_id, store_id, active);

CREATE TABLE IF NOT EXISTS recon.user_org_scopes (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    user_id uuid NOT NULL REFERENCES recon.users(id) ON DELETE CASCADE,
    organization_unit_id uuid NOT NULL REFERENCES recon.organization_units(id) ON DELETE CASCADE,
    include_descendants boolean NOT NULL DEFAULT true,
    created_by varchar(100),
    created_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_org_scope UNIQUE (user_id, organization_unit_id)
);

CREATE INDEX IF NOT EXISTS idx_user_org_scopes_tenant_user
    ON recon.user_org_scopes (tenant_id, user_id);

CREATE INDEX IF NOT EXISTS idx_user_org_scopes_tenant_org
    ON recon.user_org_scopes (tenant_id, organization_unit_id);

CREATE TABLE IF NOT EXISTS recon.tenant_auth_config (
    tenant_id varchar(100) PRIMARY KEY,
    local_login_enabled boolean NOT NULL DEFAULT true,
    preferred_login_mode varchar(20) NOT NULL DEFAULT 'LOCAL',
    oidc_enabled boolean NOT NULL DEFAULT false,
    oidc_display_name varchar(120),
    oidc_issuer_url text,
    oidc_client_id varchar(200),
    saml_enabled boolean NOT NULL DEFAULT false,
    saml_display_name varchar(120),
    saml_entity_id varchar(200),
    saml_sso_url text,
    api_key_auth_enabled boolean NOT NULL DEFAULT false,
    updated_by varchar(100),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS recon.tenant_api_keys (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    key_name varchar(120) NOT NULL,
    key_prefix varchar(24) NOT NULL,
    key_hash varchar(200) NOT NULL,
    description text,
    permission_codes text,
    active boolean NOT NULL DEFAULT true,
    all_store_access boolean NOT NULL DEFAULT true,
    allowed_store_ids text,
    last_used_at timestamp,
    last_used_by varchar(100),
    created_by varchar(100),
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT uk_tenant_api_key_prefix UNIQUE (key_prefix)
);

CREATE INDEX IF NOT EXISTS idx_tenant_api_keys_tenant_active
    ON recon.tenant_api_keys (tenant_id, active, updated_at DESC);

INSERT INTO recon.tenant_auth_config (tenant_id)
SELECT tenant_id
FROM recon.tenant_config
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO recon.permissions (id, code, name, module, description, created_at)
VALUES
    ('5d15c4b7-2e54-4d2e-9d3c-1208aa0c5001'::uuid, 'ADMIN_ORG', 'Organization Hierarchy Admin', 'ADMIN',
     'Create organization hierarchy nodes and manage district or region visibility.', now()),
    ('5d15c4b7-2e54-4d2e-9d3c-1208aa0c5002'::uuid, 'TENANT_ACCESS_MANAGE', 'Tenant Access Manage', 'ADMIN',
     'Manage tenant authentication settings and API access.', now()),
    ('5d15c4b7-2e54-4d2e-9d3c-1208aa0c5003'::uuid, 'API_ACCESS_MANAGE', 'Tenant API Keys Manage', 'ADMIN',
     'Create, revoke, and review tenant API keys.', now()),
    ('5d15c4b7-2e54-4d2e-9d3c-1208aa0c5004'::uuid, 'AUDIT_GLOBAL_VIEW', 'Global Audit View', 'AUDIT',
     'View tenant-wide audit activity beyond store-scoped visibility limits.', now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO recon.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM recon.roles r
JOIN recon.permissions p
  ON p.code IN ('ADMIN_ORG', 'TENANT_ACCESS_MANAGE', 'API_ACCESS_MANAGE', 'AUDIT_GLOBAL_VIEW')
WHERE lower(r.name) = 'admin'
  AND NOT EXISTS (
      SELECT 1
      FROM recon.role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
