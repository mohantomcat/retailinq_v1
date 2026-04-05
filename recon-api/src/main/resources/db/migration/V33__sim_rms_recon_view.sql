INSERT INTO recon.permissions (id, code, name, module, description, created_at)
VALUES
    ('3a05d367-d4d7-4d61-8c14-18a9167208fa'::uuid, 'RECON_SIM_RMS', 'SIM vs RMS Reconciliation', 'RECON',
     'View and work the SIM vs RMS reconciliation lane.', now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO recon.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM recon.roles r
JOIN recon.permissions p
  ON p.code = 'RECON_SIM_RMS'
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
        substr(md5(tc.tenant_id || '|SIM_RMS|' || defaults.severity), 1, 8) || '-' ||
        substr(md5(tc.tenant_id || '|SIM_RMS|' || defaults.severity), 9, 4) || '-' ||
        substr(md5(tc.tenant_id || '|SIM_RMS|' || defaults.severity), 13, 4) || '-' ||
        substr(md5(tc.tenant_id || '|SIM_RMS|' || defaults.severity), 17, 4) || '-' ||
        substr(md5(tc.tenant_id || '|SIM_RMS|' || defaults.severity), 21, 12)
    )::uuid,
    tc.tenant_id,
    'SIM_RMS',
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
        ('LOW', 720, 'Default SLA for low-severity SIM vs RMS exceptions.'),
        ('MEDIUM', 240, 'Default SLA for medium-severity SIM vs RMS exceptions.'),
        ('HIGH', 120, 'Default SLA for high-severity SIM vs RMS exceptions.'),
        ('CRITICAL', 60, 'Default SLA for critical SIM vs RMS exceptions.')
) AS defaults(severity, target_minutes, description)
  ON true
ON CONFLICT (tenant_id, recon_view, severity) DO NOTHING;
