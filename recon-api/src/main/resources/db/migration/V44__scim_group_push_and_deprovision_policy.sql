ALTER TABLE IF EXISTS recon.tenant_auth_config
    ADD COLUMN IF NOT EXISTS scim_group_push_enabled boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS scim_deprovision_policy varchar(40) NOT NULL DEFAULT 'DEACTIVATE';

CREATE TABLE IF NOT EXISTS recon.tenant_scim_groups (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    display_name varchar(255) NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    updated_by varchar(120)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_tenant_scim_groups_name_unique
    ON recon.tenant_scim_groups (tenant_id, lower(display_name));

CREATE TABLE IF NOT EXISTS recon.user_scim_group_memberships (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    user_id uuid NOT NULL REFERENCES recon.users(id) ON DELETE CASCADE,
    external_group varchar(255) NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    updated_by varchar(120)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_scim_group_memberships_user_group_unique
    ON recon.user_scim_group_memberships (tenant_id, user_id, lower(external_group));

CREATE INDEX IF NOT EXISTS idx_user_scim_group_memberships_group_lookup
    ON recon.user_scim_group_memberships (tenant_id, lower(external_group));

CREATE INDEX IF NOT EXISTS idx_user_scim_group_memberships_user_lookup
    ON recon.user_scim_group_memberships (tenant_id, user_id);
