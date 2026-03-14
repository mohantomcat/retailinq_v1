ALTER TABLE recon.siocs_poll_checkpoint
    ADD COLUMN IF NOT EXISTS last_processed_id BIGINT DEFAULT 0;

ALTER TABLE recon.siocs_poll_checkpoint
    ADD COLUMN IF NOT EXISTS lock_owner VARCHAR(128);

ALTER TABLE recon.siocs_poll_checkpoint
    ADD COLUMN IF NOT EXISTS lock_expires_at TIMESTAMP;
