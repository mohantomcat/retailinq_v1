CREATE TABLE IF NOT EXISTS recon.exception_cases (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    transaction_key varchar(255) NOT NULL,
    recon_view varchar(50) NOT NULL,
    case_status varchar(30) NOT NULL DEFAULT 'OPEN',
    reason_code varchar(80),
    assignee_username varchar(100),
    notes text,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT uk_exception_case UNIQUE (tenant_id, transaction_key, recon_view)
);

CREATE INDEX IF NOT EXISTS idx_exception_cases_tenant_recon
    ON recon.exception_cases (tenant_id, recon_view, case_status, updated_at DESC);

CREATE TABLE IF NOT EXISTS recon.exception_comments (
    id uuid PRIMARY KEY,
    case_id uuid NOT NULL REFERENCES recon.exception_cases(id) ON DELETE CASCADE,
    comment_text text NOT NULL,
    created_by varchar(100) NOT NULL,
    created_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_exception_comments_case
    ON recon.exception_comments (case_id, created_at ASC);

INSERT INTO recon.permissions (id, code, name, module, description, created_at)
VALUES
    ('5d15c4b7-2e54-4d2e-9d3c-1208aa0c2001'::uuid, 'EXCEPTION_VIEW', 'Exception Workbench View', 'EXCEPTIONS',
     'View exception cases, assignments, and comments.', now()),
    ('5d15c4b7-2e54-4d2e-9d3c-1208aa0c2002'::uuid, 'EXCEPTION_EDIT', 'Exception Workbench Edit', 'EXCEPTIONS',
     'Create and update exception cases, assignments, and comments.', now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO recon.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM recon.roles r
JOIN recon.permissions p
  ON p.code IN ('EXCEPTION_VIEW', 'EXCEPTION_EDIT')
WHERE lower(r.name) = 'admin'
  AND NOT EXISTS (
      SELECT 1
      FROM recon.role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
