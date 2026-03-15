ALTER TABLE IF EXISTS recon.cloud_ingestion_checkpoint
    RENAME TO siocs_ingestion_checkpoint;

ALTER TABLE IF EXISTS recon.cloud_ingestion_raw
    RENAME TO siocs_ingestion_raw;

ALTER TABLE IF EXISTS recon.cloud_ingestion_transaction
    RENAME TO siocs_ingestion_transaction;

ALTER TABLE IF EXISTS recon.cloud_ingestion_error
    RENAME TO siocs_ingestion_error;

ALTER INDEX IF EXISTS recon.ux_cloud_ingestion_txn_business
    RENAME TO ux_siocs_ingestion_txn_business;

ALTER INDEX IF EXISTS recon.ix_cloud_ingestion_txn_status
    RENAME TO ix_siocs_ingestion_txn_status;

ALTER INDEX IF EXISTS recon.ix_cloud_ingestion_txn_external_id
    RENAME TO ix_siocs_ingestion_txn_external_id;

ALTER INDEX IF EXISTS recon.ux_cloud_ingestion_txn_dedup
    RENAME TO ux_siocs_ingestion_txn_dedup;

UPDATE recon.siocs_ingestion_checkpoint
SET source_name = 'SIOCS'
WHERE source_name IS NULL
   OR source_name = ''
   OR source_name = 'CLOUD_SIM';

UPDATE recon.siocs_ingestion_raw
SET source_name = 'SIOCS'
WHERE source_name IS NULL
   OR source_name = ''
   OR source_name = 'CLOUD_SIM';

UPDATE recon.siocs_ingestion_transaction
SET source_name = 'SIOCS'
WHERE source_name IS NULL
   OR source_name = ''
   OR source_name = 'CLOUD_SIM';

UPDATE recon.siocs_ingestion_error
SET source_name = 'SIOCS'
WHERE source_name IS NULL
   OR source_name = ''
   OR source_name = 'CLOUD_SIM';
