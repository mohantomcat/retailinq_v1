CREATE TABLE IF NOT EXISTS recon.operations_action_audit (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    module_id varchar(80) NOT NULL,
    action_key varchar(80) NOT NULL,
    action_scope varchar(30) NOT NULL DEFAULT 'SAFE',
    requested_by varchar(100) NOT NULL,
    request_payload jsonb,
    result_status varchar(30) NOT NULL,
    result_message text,
    created_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_operations_action_audit_tenant_created
    ON recon.operations_action_audit (tenant_id, created_at DESC);

INSERT INTO recon.permissions (id, code, name, module, description, created_at)
VALUES
    ('5d15c4b7-2e54-4d2e-9d3c-1208aa0c4001'::uuid, 'OPS_VIEW', 'Operations View', 'OPERATIONS',
     'View connector status, checkpoints, and safe operational controls.', now()),
    ('5d15c4b7-2e54-4d2e-9d3c-1208aa0c4002'::uuid, 'OPS_EXECUTE_SAFE', 'Operations Execute Safe', 'OPERATIONS',
     'Trigger safe operational actions such as run now, release stale claims, and requeue failed work.', now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO recon.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM recon.roles r
JOIN recon.permissions p
  ON p.code IN ('OPS_VIEW', 'OPS_EXECUTE_SAFE')
WHERE lower(r.name) = 'admin'
  AND NOT EXISTS (
      SELECT 1
      FROM recon.role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
