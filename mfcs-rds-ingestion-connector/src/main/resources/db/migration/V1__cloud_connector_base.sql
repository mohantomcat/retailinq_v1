CREATE SCHEMA IF NOT EXISTS recon;

CREATE TABLE IF NOT EXISTS recon.mfcs_ingestion_checkpoint (
    connector_name VARCHAR(100) PRIMARY KEY,
    source_name VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    last_cursor TEXT NOT NULL DEFAULT '',
    last_success_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_polled_timestamp TIMESTAMP NULL,
    last_status VARCHAR(32) NOT NULL DEFAULT 'READY',
    last_error_message TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS recon.mfcs_ingestion_raw (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    source_name VARCHAR(100) NOT NULL,
    source_record_key VARCHAR(255) NOT NULL,
    source_cursor TEXT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    payload_json JSONB NOT NULL,
    fetched_at TIMESTAMP NOT NULL,
    request_id VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS recon.mfcs_ingestion_transaction (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    source_name VARCHAR(100) NOT NULL,
    source_record_key VARCHAR(255) NOT NULL,
    source_cursor TEXT NULL,
    external_id VARCHAR(64) NOT NULL,
    transaction_extended_id VARCHAR(255) NULL,
    request_id BIGINT NULL,
    store_id VARCHAR(64) NULL,
    transaction_date_time TIMESTAMP NULL,
    update_date_time TIMESTAMP NULL,
    type INTEGER NULL,
    processing_status INTEGER NULL,
    line_id BIGINT NULL,
    item_id VARCHAR(255) NULL,
    quantity NUMERIC(18, 4) NULL,
    unit_of_measure VARCHAR(64) NULL,
    raw_payload_id BIGINT NOT NULL REFERENCES recon.mfcs_ingestion_raw (id),
    ingestion_status VARCHAR(32) NOT NULL DEFAULT 'READY',
    retry_count INTEGER NOT NULL DEFAULT 0,
    locked_by VARCHAR(100) NULL,
    locked_at TIMESTAMP NULL,
    last_error_message TEXT NULL,
    published_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_mfcs_ingestion_txn_business
    ON recon.mfcs_ingestion_transaction (
        tenant_id, source_name, external_id,
        transaction_extended_id, item_id, type, update_date_time
    );

CREATE INDEX IF NOT EXISTS ix_mfcs_ingestion_txn_status
    ON recon.mfcs_ingestion_transaction (ingestion_status, retry_count, update_date_time);

CREATE INDEX IF NOT EXISTS ix_mfcs_ingestion_txn_external_id
    ON recon.mfcs_ingestion_transaction (external_id);

CREATE TABLE IF NOT EXISTS recon.mfcs_ingestion_error (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    source_name VARCHAR(100) NOT NULL,
    source_record_key VARCHAR(255) NOT NULL,
    payload_json JSONB NOT NULL,
    error_type VARCHAR(100) NOT NULL,
    error_message TEXT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
