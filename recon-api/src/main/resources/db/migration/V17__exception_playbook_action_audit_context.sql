ALTER TABLE recon.operations_action_audit
    ADD COLUMN IF NOT EXISTS transaction_key varchar(255),
    ADD COLUMN IF NOT EXISTS recon_view varchar(50),
    ADD COLUMN IF NOT EXISTS playbook_id uuid,
    ADD COLUMN IF NOT EXISTS playbook_step_id uuid,
    ADD COLUMN IF NOT EXISTS playbook_step_title varchar(200);

CREATE INDEX IF NOT EXISTS idx_operations_action_audit_case_context
    ON recon.operations_action_audit (tenant_id, transaction_key, recon_view, created_at DESC);
