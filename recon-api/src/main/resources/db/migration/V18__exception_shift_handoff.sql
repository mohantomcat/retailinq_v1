ALTER TABLE recon.exception_cases
    ADD COLUMN IF NOT EXISTS next_action varchar(255),
    ADD COLUMN IF NOT EXISTS next_action_due_at timestamp,
    ADD COLUMN IF NOT EXISTS handoff_note text,
    ADD COLUMN IF NOT EXISTS last_handoff_by varchar(100),
    ADD COLUMN IF NOT EXISTS last_handoff_at timestamp;

CREATE INDEX IF NOT EXISTS idx_exception_cases_next_action_due_at
    ON recon.exception_cases (tenant_id, recon_view, next_action_due_at, case_status);
