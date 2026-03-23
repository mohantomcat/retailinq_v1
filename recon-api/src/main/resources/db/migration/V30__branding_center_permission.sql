INSERT INTO recon.permissions (id, code, name, module, description, created_at)
VALUES (
    '5d15c4b7-2e54-4d2e-9d3c-1208aa0c5005'::uuid,
    'BRANDING_MANAGE',
    'Branding Center Manage',
    'ADMIN',
    'View and manage tenant branding assets for the Branding Center.',
    now()
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO recon.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM recon.roles r
JOIN recon.permissions p
  ON p.code = 'BRANDING_MANAGE'
WHERE lower(r.name) = 'admin'
  AND NOT EXISTS (
      SELECT 1
      FROM recon.role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
