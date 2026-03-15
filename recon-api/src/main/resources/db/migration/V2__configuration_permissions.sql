INSERT INTO recon.permissions (id, code, name, module, description, created_at)
VALUES
    ('5d15c4b7-2e54-4d2e-9d3c-1208aa0c1001'::uuid, 'CONFIG_MODULE_VIEW', 'Module Configurations View', 'CONFIGURATIONS',
     'View module-specific configuration entries.', now()),
    ('5d15c4b7-2e54-4d2e-9d3c-1208aa0c1002'::uuid, 'CONFIG_SYSTEM_VIEW', 'System Configurations View', 'CONFIGURATIONS',
     'View shared system-wide configuration entries.', now()),
    ('5d15c4b7-2e54-4d2e-9d3c-1208aa0c1003'::uuid, 'CONFIG_EDIT', 'Configuration Edit', 'CONFIGURATIONS',
     'Create, update, and clear supported configuration overrides.', now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO recon.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM recon.roles r
JOIN recon.permissions p
  ON p.code IN ('CONFIG_MODULE_VIEW', 'CONFIG_SYSTEM_VIEW', 'CONFIG_EDIT')
WHERE lower(r.name) = 'admin'
  AND NOT EXISTS (
      SELECT 1
      FROM recon.role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
