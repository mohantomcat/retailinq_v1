ALTER TABLE recon.xocs_ingestion_transaction
    ALTER COLUMN poslog_payload DROP NOT NULL;

ALTER TABLE recon.xocs_ingestion_transaction
    ALTER COLUMN payload_encoding DROP NOT NULL;
