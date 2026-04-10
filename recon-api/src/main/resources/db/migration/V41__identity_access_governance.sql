ALTER TABLE IF EXISTS recon.users
    ADD COLUMN IF NOT EXISTS identity_provider varchar(40) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN IF NOT EXISTS external_subject varchar(300),
    ADD COLUMN IF NOT EXISTS email_verified boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS access_review_status varchar(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS last_access_review_at timestamp,
    ADD COLUMN IF NOT EXISTS last_access_review_by varchar(100),
    ADD COLUMN IF NOT EXISTS access_review_due_at timestamp;

UPDATE recon.users
SET identity_provider = COALESCE(NULLIF(identity_provider, ''), 'LOCAL'),
    access_review_status = COALESCE(NULLIF(access_review_status, ''), 'PENDING'),
    access_review_due_at = COALESCE(access_review_due_at, now() + interval '90 days');

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_tenant_external_subject_unique
    ON recon.users (tenant_id, identity_provider, lower(external_subject))
    WHERE external_subject IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_tenant_access_review
    ON recon.users (tenant_id, access_review_status, access_review_due_at);

ALTER TABLE IF EXISTS recon.tenant_auth_config
    ADD COLUMN IF NOT EXISTS auto_provision_users boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS allowed_email_domains text,
    ADD COLUMN IF NOT EXISTS oidc_username_claim varchar(80) NOT NULL DEFAULT 'preferred_username',
    ADD COLUMN IF NOT EXISTS oidc_email_claim varchar(80) NOT NULL DEFAULT 'email',
    ADD COLUMN IF NOT EXISTS oidc_groups_claim varchar(80) NOT NULL DEFAULT 'groups',
    ADD COLUMN IF NOT EXISTS saml_email_attribute varchar(120),
    ADD COLUMN IF NOT EXISTS saml_groups_attribute varchar(120);

INSERT INTO recon.permissions (id, code, name, module, description, created_at)
VALUES
    ('5d15c4b7-2e54-4d2e-9d3c-1208aa0c6001'::uuid, 'ACCESS_REVIEW_MANAGE',
     'Access Review Manage', 'ADMIN',
     'Certify user access and manage enterprise access review findings.', now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO recon.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM recon.roles r
JOIN recon.permissions p
  ON p.code = 'ACCESS_REVIEW_MANAGE'
WHERE lower(r.name) = 'admin'
  AND NOT EXISTS (
      SELECT 1
      FROM recon.role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
