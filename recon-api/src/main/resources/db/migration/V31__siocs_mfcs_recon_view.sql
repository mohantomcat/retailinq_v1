INSERT INTO recon.permissions (id, code, name, module, description, created_at)
VALUES
    ('0fbc8f31-2dd8-4aa3-9442-bd18b2b93001'::uuid, 'RECON_SIOCS_MFCS', 'SIOCS vs MFCS Reconciliation', 'RECON',
     'View and work the SIOCS vs MFCS reconciliation lane.', now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO recon.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM recon.roles r
JOIN recon.permissions p
  ON p.code = 'RECON_SIOCS_MFCS'
WHERE lower(r.name) = 'admin'
  AND NOT EXISTS (
      SELECT 1
      FROM recon.role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );

INSERT INTO recon.exception_sla_rules (
    id,
    tenant_id,
    recon_view,
    severity,
    target_minutes,
    description,
    created_by,
    updated_by,
    created_at,
    updated_at
)
SELECT
    (
        substr(md5(tc.tenant_id || '|SIOCS_MFCS|' || defaults.severity), 1, 8) || '-' ||
        substr(md5(tc.tenant_id || '|SIOCS_MFCS|' || defaults.severity), 9, 4) || '-' ||
        substr(md5(tc.tenant_id || '|SIOCS_MFCS|' || defaults.severity), 13, 4) || '-' ||
        substr(md5(tc.tenant_id || '|SIOCS_MFCS|' || defaults.severity), 17, 4) || '-' ||
        substr(md5(tc.tenant_id || '|SIOCS_MFCS|' || defaults.severity), 21, 12)
    )::uuid,
    tc.tenant_id,
    'SIOCS_MFCS',
    defaults.severity,
    defaults.target_minutes,
    defaults.description,
    'system',
    'system',
    now(),
    now()
FROM recon.tenant_config tc
JOIN (
    VALUES
        ('LOW', 720, 'Default SLA for low-severity SIOCS vs MFCS exceptions.'),
        ('MEDIUM', 240, 'Default SLA for medium-severity SIOCS vs MFCS exceptions.'),
        ('HIGH', 120, 'Default SLA for high-severity SIOCS vs MFCS exceptions.'),
        ('CRITICAL', 60, 'Default SLA for critical SIOCS vs MFCS exceptions.')
) AS defaults(severity, target_minutes, description)
  ON true
ON CONFLICT (tenant_id, recon_view, severity) DO NOTHING;
