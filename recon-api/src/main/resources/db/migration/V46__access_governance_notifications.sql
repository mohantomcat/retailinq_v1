ALTER TABLE IF EXISTS recon.tenant_auth_config
    ADD COLUMN IF NOT EXISTS manager_access_review_reminders_enabled boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS manager_access_review_reminder_interval_days integer NOT NULL DEFAULT 7,
    ADD COLUMN IF NOT EXISTS manager_access_review_additional_emails text,
    ADD COLUMN IF NOT EXISTS manager_access_review_teams_webhook_url text,
    ADD COLUMN IF NOT EXISTS manager_access_review_slack_webhook_url text,
    ADD COLUMN IF NOT EXISTS privileged_action_alerts_enabled boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS privileged_action_alert_email_recipients text,
    ADD COLUMN IF NOT EXISTS privileged_action_alert_teams_webhook_url text,
    ADD COLUMN IF NOT EXISTS privileged_action_alert_slack_webhook_url text;

ALTER TABLE IF EXISTS recon.users
    ADD COLUMN IF NOT EXISTS access_review_last_reminder_at timestamp;

CREATE INDEX IF NOT EXISTS idx_users_access_review_notifications
    ON recon.users (tenant_id, access_review_status, manager_user_id, access_review_last_reminder_at);
