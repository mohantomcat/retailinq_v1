INSERT INTO recon.permissions (id, code, name, module, description, created_at)
VALUES
    ('5d15c4b7-2e54-4d2e-9d3c-1208aa0c4004'::uuid, 'OPS_EXECUTE_ADVANCED', 'Operations Execute Advanced', 'OPERATIONS',
     'Execute advanced operational actions such as replay windows and targeted recovery actions.', now()),
    ('5d15c4b7-2e54-4d2e-9d3c-1208aa0c4005'::uuid, 'OPS_CHECKPOINT_RESET', 'Operations Checkpoint Reset', 'OPERATIONS',
     'Reset connector checkpoints to a chosen timestamp or business date.', now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO recon.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM recon.roles r
JOIN recon.permissions p
  ON p.code IN ('OPS_EXECUTE_ADVANCED', 'OPS_CHECKPOINT_RESET')
WHERE lower(r.name) = 'admin'
  AND NOT EXISTS (
      SELECT 1
      FROM recon.role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
