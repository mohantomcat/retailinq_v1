ALTER TABLE recon.exception_cases
    ADD COLUMN IF NOT EXISTS assigned_role_name varchar(100);

CREATE INDEX IF NOT EXISTS idx_exception_cases_assignment
    ON recon.exception_cases (tenant_id, recon_view, assigned_role_name, assignee_username, case_status, due_at);

INSERT INTO recon.permissions (id, code, name, module, description, created_at)
VALUES
    ('87cb1041-b460-4b6e-8463-cf5b8f5d3f01'::uuid, 'EXCEPTION_QUEUE_VIEW', 'Exception Queue View', 'EXCEPTIONS',
     'View role-based exception queues, mine/unassigned queues, and SLA exception backlogs.', now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO recon.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM recon.roles r
JOIN recon.permissions p
  ON p.code IN ('EXCEPTION_QUEUE_VIEW')
WHERE lower(r.name) = 'admin'
  AND NOT EXISTS (
      SELECT 1
      FROM recon.role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
