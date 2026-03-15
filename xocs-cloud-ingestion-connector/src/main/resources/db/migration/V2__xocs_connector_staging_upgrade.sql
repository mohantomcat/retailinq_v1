ALTER TABLE recon.xocs_ingestion_checkpoint
    ADD COLUMN IF NOT EXISTS last_run_started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_run_completed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_run_status VARCHAR(20);

ALTER TABLE recon.xocs_ingestion_raw
    ADD COLUMN IF NOT EXISTS connector_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS request_from_update_ts TIMESTAMP,
    ADD COLUMN IF NOT EXISTS request_last_cursor_id BIGINT,
    ADD COLUMN IF NOT EXISTS request_limit INTEGER,
    ADD COLUMN IF NOT EXISTS response_record_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS http_status INTEGER,
    ADD COLUMN IF NOT EXISTS content_hash VARCHAR(128);

UPDATE recon.xocs_ingestion_raw
SET connector_name = COALESCE(connector_name, source_name)
WHERE connector_name IS NULL;

ALTER TABLE recon.xocs_ingestion_raw
    ALTER COLUMN connector_name SET NOT NULL;

ALTER TABLE recon.xocs_ingestion_transaction
    ADD COLUMN IF NOT EXISTS transaction_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS begin_datetime TIMESTAMP,
    ADD COLUMN IF NOT EXISTS end_datetime TIMESTAMP,
    ADD COLUMN IF NOT EXISTS session_id BIGINT,
    ADD COLUMN IF NOT EXISTS operator_party_id BIGINT,
    ADD COLUMN IF NOT EXISTS trans_typcode VARCHAR(30),
    ADD COLUMN IF NOT EXISTS trans_statcode VARCHAR(30),
    ADD COLUMN IF NOT EXISTS trans_total NUMERIC(18,6),
    ADD COLUMN IF NOT EXISTS trans_subtotal NUMERIC(18,6),
    ADD COLUMN IF NOT EXISTS trans_taxtotal NUMERIC(18,6),
    ADD COLUMN IF NOT EXISTS trans_roundtotal NUMERIC(18,6),
    ADD COLUMN IF NOT EXISTS line_count INTEGER,
    ADD COLUMN IF NOT EXISTS distinct_item_count INTEGER,
    ADD COLUMN IF NOT EXISTS total_item_qty NUMERIC(18,4),
    ADD COLUMN IF NOT EXISTS sum_extended_amt NUMERIC(18,6),
    ADD COLUMN IF NOT EXISTS sum_net_amt NUMERIC(18,6),
    ADD COLUMN IF NOT EXISTS sum_gross_amt NUMERIC(18,6),
    ADD COLUMN IF NOT EXISTS source_update_date TIMESTAMP,
    ADD COLUMN IF NOT EXISTS claimed_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP;

UPDATE recon.xocs_ingestion_transaction
SET source_update_date = COALESCE(source_update_date, update_date_time)
WHERE source_update_date IS NULL;

UPDATE recon.xocs_ingestion_transaction
SET transaction_key = COALESCE(transaction_key, organization_id || '|' || external_id)
WHERE transaction_key IS NULL AND external_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_xocs_ingestion_txn_business
    ON recon.xocs_ingestion_transaction (organization_id, rtl_loc_id, business_date, wkstn_id, trans_seq);

CREATE UNIQUE INDEX IF NOT EXISTS uq_xocs_ingestion_txn_transaction_key
    ON recon.xocs_ingestion_transaction (transaction_key)
    WHERE transaction_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_xocs_ingestion_txn_filter
    ON recon.xocs_ingestion_transaction (organization_id, rtl_loc_id, business_date, wkstn_id);

CREATE INDEX IF NOT EXISTS idx_xocs_ingestion_txn_source_update
    ON recon.xocs_ingestion_transaction (source_update_date, trans_seq);

CREATE TABLE IF NOT EXISTS recon.xocs_ingestion_line (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES recon.xocs_ingestion_transaction(id) ON DELETE CASCADE,
    organization_id BIGINT NOT NULL,
    rtl_loc_id BIGINT NOT NULL,
    business_date DATE NOT NULL,
    wkstn_id BIGINT NOT NULL,
    trans_seq BIGINT NOT NULL,
    transaction_key VARCHAR(128) NOT NULL,
    rtrans_lineitm_seq BIGINT NOT NULL,
    transaction_line_key VARCHAR(160) NOT NULL,
    item_id VARCHAR(60) NOT NULL,
    scanned_item_id VARCHAR(60),
    unit_of_measure VARCHAR(30),
    return_flag INTEGER,
    void_flag INTEGER,
    line_business_type VARCHAR(20) NOT NULL,
    raw_quantity NUMERIC(11,4),
    normalized_quantity NUMERIC(11,4),
    unit_price NUMERIC(18,6),
    raw_extended_amt NUMERIC(18,6),
    raw_net_amt NUMERIC(18,6),
    gross_amt NUMERIC(18,6),
    vat_amt NUMERIC(18,6),
    sale_lineitm_typcode VARCHAR(30),
    rtrans_lineitm_typcode VARCHAR(30),
    rtrans_lineitm_statcode VARCHAR(30),
    inventory_action_code VARCHAR(30),
    serial_nbr VARCHAR(60),
    source_update_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_xocs_ingestion_line_business UNIQUE (
        organization_id, rtl_loc_id, business_date, wkstn_id, trans_seq, rtrans_lineitm_seq
    ),
    CONSTRAINT uq_xocs_ingestion_line_key UNIQUE (transaction_line_key)
);

CREATE INDEX IF NOT EXISTS idx_xocs_ingestion_line_txn_id
    ON recon.xocs_ingestion_line (transaction_id);

CREATE INDEX IF NOT EXISTS idx_xocs_ingestion_line_filter
    ON recon.xocs_ingestion_line (organization_id, rtl_loc_id, business_date, wkstn_id);

CREATE INDEX IF NOT EXISTS idx_xocs_ingestion_line_compare
    ON recon.xocs_ingestion_line (transaction_key, line_business_type, item_id);

ALTER TABLE recon.xocs_ingestion_error
    ADD COLUMN IF NOT EXISTS connector_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS stage VARCHAR(30),
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS retryable BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE recon.xocs_ingestion_error
SET connector_name = COALESCE(connector_name, source_name),
    stage = COALESCE(stage, 'INGESTION'),
    error_code = COALESCE(error_code, error_type)
WHERE connector_name IS NULL OR stage IS NULL OR error_code IS NULL;

CREATE INDEX IF NOT EXISTS idx_xocs_ingestion_error_stage
    ON recon.xocs_ingestion_error (stage, created_at DESC);
