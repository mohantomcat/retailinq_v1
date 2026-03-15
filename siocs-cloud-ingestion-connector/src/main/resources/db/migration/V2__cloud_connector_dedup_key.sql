ALTER TABLE recon.cloud_ingestion_transaction
    ADD COLUMN IF NOT EXISTS dedup_key VARCHAR(512);

UPDATE recon.cloud_ingestion_transaction
SET dedup_key = CONCAT_WS('|',
    COALESCE(tenant_id, ''),
    COALESCE(source_name, ''),
    COALESCE(external_id, ''),
    COALESCE(TO_CHAR(update_date_time, 'YYYY-MM-DD"T"HH24:MI:SS.MS'), ''),
    CASE
        WHEN line_id IS NOT NULL THEN line_id::TEXT
        ELSE CONCAT_WS('|',
            COALESCE(item_id, ''),
            COALESCE(transaction_extended_id, ''),
            COALESCE(type::TEXT, '')
        )
    END
)
WHERE dedup_key IS NULL;

ALTER TABLE recon.cloud_ingestion_transaction
    ALTER COLUMN dedup_key SET NOT NULL;

DROP INDEX IF EXISTS recon.ux_cloud_ingestion_txn_business;

CREATE UNIQUE INDEX IF NOT EXISTS ux_cloud_ingestion_txn_dedup
    ON recon.cloud_ingestion_transaction (dedup_key);
