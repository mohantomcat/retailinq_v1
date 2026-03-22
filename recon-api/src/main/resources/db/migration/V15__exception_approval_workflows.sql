CREATE TABLE IF NOT EXISTS recon.exception_closure_policies (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    policy_name varchar(150) NOT NULL,
    recon_view varchar(50) NOT NULL,
    target_status varchar(30) NOT NULL,
    min_severity varchar(20) NOT NULL DEFAULT 'HIGH',
    require_reason_code boolean NOT NULL DEFAULT false,
    require_root_cause_category boolean NOT NULL DEFAULT false,
    require_notes boolean NOT NULL DEFAULT false,
    require_comment boolean NOT NULL DEFAULT false,
    require_approval boolean NOT NULL DEFAULT false,
    approver_role_name varchar(100),
    active boolean NOT NULL DEFAULT true,
    description text,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_exception_closure_policies_tenant_recon
    ON recon.exception_closure_policies (tenant_id, recon_view, target_status, active, updated_at DESC);

CREATE TABLE IF NOT EXISTS recon.exception_approval_requests (
    id uuid PRIMARY KEY,
    case_id uuid NOT NULL REFERENCES recon.exception_cases(id) ON DELETE CASCADE,
    policy_id uuid NOT NULL REFERENCES recon.exception_closure_policies(id) ON DELETE CASCADE,
    tenant_id varchar(100) NOT NULL,
    transaction_key varchar(255) NOT NULL,
    recon_view varchar(50) NOT NULL,
    previous_case_status varchar(30) NOT NULL,
    requested_case_status varchar(30) NOT NULL,
    requested_severity varchar(20) NOT NULL,
    requested_reason_code varchar(80),
    requested_root_cause_category varchar(80),
    requested_assignee_username varchar(100),
    requested_assigned_role_name varchar(100),
    requested_notes text,
    closure_comment text,
    approver_role_name varchar(100),
    request_status varchar(20) NOT NULL DEFAULT 'PENDING',
    requested_by varchar(100) NOT NULL,
    decision_by varchar(100),
    decision_notes text,
    requested_at timestamp NOT NULL DEFAULT now(),
    decision_at timestamp,
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_exception_approval_requests_tenant_recon
    ON recon.exception_approval_requests (tenant_id, recon_view, request_status, requested_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_exception_approval_pending_case
    ON recon.exception_approval_requests (case_id)
    WHERE request_status = 'PENDING';

INSERT INTO recon.permissions (id, code, name, module, description, created_at)
VALUES
    ('cb1fae4a-f0e8-4e9c-9bf3-45ed7a2d0501'::uuid, 'EXCEPTION_APPROVAL_VIEW', 'Exception Approval View', 'EXCEPTIONS',
     'View approval queues, pending decisions, and workflow rules for exception closure.', now()),
    ('cb1fae4a-f0e8-4e9c-9bf3-45ed7a2d0502'::uuid, 'EXCEPTION_APPROVAL_EDIT', 'Exception Approval Decision', 'EXCEPTIONS',
     'Approve or reject pending exception workflow requests.', now()),
    ('cb1fae4a-f0e8-4e9c-9bf3-45ed7a2d0503'::uuid, 'EXCEPTION_POLICY_EDIT', 'Exception Workflow Policy Edit', 'EXCEPTIONS',
     'Create and maintain closure rules and approval requirements for exception workflows.', now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO recon.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM recon.roles r
JOIN recon.permissions p
  ON p.code IN ('EXCEPTION_APPROVAL_VIEW', 'EXCEPTION_APPROVAL_EDIT', 'EXCEPTION_POLICY_EDIT')
WHERE lower(r.name) = 'admin'
  AND NOT EXISTS (
      SELECT 1
      FROM recon.role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
