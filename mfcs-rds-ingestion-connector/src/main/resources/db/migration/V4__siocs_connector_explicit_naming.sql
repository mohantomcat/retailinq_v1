-- Normalize legacy source labels from earlier connector drafts.
UPDATE recon.mfcs_ingestion_checkpoint
SET source_name = 'MFCS'
WHERE source_name IS NULL
   OR source_name = ''
   OR source_name = 'CLOUD_SIM'
   OR source_name = 'MFCS_SIM';

UPDATE recon.mfcs_ingestion_raw
SET source_name = 'MFCS'
WHERE source_name IS NULL
   OR source_name = ''
   OR source_name = 'CLOUD_SIM'
   OR source_name = 'MFCS_SIM';

UPDATE recon.mfcs_ingestion_transaction
SET source_name = 'MFCS'
WHERE source_name IS NULL
   OR source_name = ''
   OR source_name = 'CLOUD_SIM'
   OR source_name = 'MFCS_SIM';

UPDATE recon.mfcs_ingestion_error
SET source_name = 'MFCS'
WHERE source_name IS NULL
   OR source_name = ''
   OR source_name = 'CLOUD_SIM'
   OR source_name = 'MFCS_SIM';
