ALTER TABLE recon.mfcs_ingestion_checkpoint
    ADD COLUMN IF NOT EXISTS last_cursor_external_id TEXT;

ALTER TABLE recon.mfcs_ingestion_checkpoint
    ADD COLUMN IF NOT EXISTS last_cursor_id BIGINT;

UPDATE recon.mfcs_ingestion_checkpoint
SET last_cursor_external_id = COALESCE(last_cursor_external_id, ''),
    last_cursor_id = COALESCE(last_cursor_id, 0);

ALTER TABLE recon.mfcs_ingestion_checkpoint
    ALTER COLUMN last_cursor_external_id SET DEFAULT '';

ALTER TABLE recon.mfcs_ingestion_checkpoint
    ALTER COLUMN last_cursor_id SET DEFAULT 0;
