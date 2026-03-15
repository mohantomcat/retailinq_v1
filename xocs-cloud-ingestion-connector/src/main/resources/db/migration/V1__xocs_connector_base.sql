CREATE TABLE IF NOT EXISTS recon.xocs_ingestion_checkpoint (
    connector_name VARCHAR(100) PRIMARY KEY,
    source_name VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    last_cursor_id BIGINT,
    last_success_timestamp TIMESTAMP,
    last_poll_started_at TIMESTAMP,
    last_poll_completed_at TIMESTAMP,
    last_poll_status VARCHAR(20),
    last_error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS recon.xocs_ingestion_raw (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    source_name VARCHAR(100) NOT NULL,
    source_record_key VARCHAR(256),
    source_cursor VARCHAR(256),
    payload_json TEXT NOT NULL,
    request_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS recon.xocs_ingestion_transaction (
    id BIGSERIAL PRIMARY KEY,
    dedup_key VARCHAR(512) NOT NULL UNIQUE,
    tenant_id VARCHAR(100) NOT NULL,
    source_name VARCHAR(100) NOT NULL,
    source_record_key VARCHAR(256),
    source_cursor VARCHAR(256),
    organization_id BIGINT NOT NULL,
    rtl_loc_id BIGINT NOT NULL,
    business_date DATE NOT NULL,
    wkstn_id BIGINT NOT NULL,
    trans_seq BIGINT NOT NULL,
    external_id VARCHAR(128),
    poslog_payload TEXT NOT NULL,
    payload_encoding VARCHAR(32),
    create_date TIMESTAMP,
    update_date_time TIMESTAMP,
    raw_payload_id BIGINT NOT NULL REFERENCES recon.xocs_ingestion_raw(id),
    ingestion_status VARCHAR(20) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    locked_by VARCHAR(128),
    locked_at TIMESTAMP,
    last_error_message TEXT,
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_xocs_ingestion_txn_status
    ON recon.xocs_ingestion_transaction (ingestion_status, update_date_time, id);

CREATE TABLE IF NOT EXISTS recon.xocs_ingestion_error (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    source_name VARCHAR(100) NOT NULL,
    source_record_key VARCHAR(256),
    payload_json TEXT NOT NULL,
    error_type VARCHAR(64) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
