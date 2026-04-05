CREATE TABLE IF NOT EXISTS recon.rms_poll_checkpoint (
    poller_id VARCHAR(128) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    last_processed_timestamp TIMESTAMP NOT NULL DEFAULT TIMESTAMP '1970-01-01 00:00:00',
    last_processed_external_id VARCHAR(256) NOT NULL DEFAULT '',
    last_processed_id BIGINT NOT NULL DEFAULT 0,
    last_poll_started_at TIMESTAMP,
    last_poll_completed_at TIMESTAMP,
    last_poll_status VARCHAR(32) NOT NULL DEFAULT 'READY',
    last_error_message TEXT,
    total_records_polled BIGINT NOT NULL DEFAULT 0,
    lock_owner VARCHAR(128),
    lock_expires_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE recon.rms_poll_checkpoint
    ADD COLUMN IF NOT EXISTS last_processed_id BIGINT DEFAULT 0;

ALTER TABLE recon.rms_poll_checkpoint
    ADD COLUMN IF NOT EXISTS lock_owner VARCHAR(128);

ALTER TABLE recon.rms_poll_checkpoint
    ADD COLUMN IF NOT EXISTS lock_expires_at TIMESTAMP;
