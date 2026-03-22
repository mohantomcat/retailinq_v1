ALTER TABLE recon.exception_cases
    ADD COLUMN IF NOT EXISTS root_cause_category varchar(80);

UPDATE recon.exception_cases
SET reason_code = CASE reason_code
    WHEN 'QUANTITY_MISMATCH' THEN 'QUANTITY_VARIANCE'
    WHEN 'TOTAL_MISMATCH' THEN 'TOTAL_CALCULATION_MISMATCH'
    WHEN 'ITEM_MAPPING' THEN 'ITEM_SYNC_GAP'
    WHEN 'DATA_QUALITY' THEN 'SOURCE_DATA_ISSUE'
    WHEN 'CONFIGURATION' THEN 'CONFIGURATION_ISSUE'
    ELSE reason_code
END
WHERE reason_code IN (
    'QUANTITY_MISMATCH',
    'TOTAL_MISMATCH',
    'ITEM_MAPPING',
    'DATA_QUALITY',
    'CONFIGURATION'
);

UPDATE recon.exception_cases
SET root_cause_category = CASE
    WHEN reason_code = 'REPLICATION_LAG' THEN 'INTEGRATION_TIMING'
    WHEN reason_code = 'DUPLICATE_SUBMISSION' THEN 'DUPLICATE_PROCESSING'
    WHEN reason_code = 'ITEM_SYNC_GAP' THEN 'ITEM_SYNC_GAP'
    WHEN reason_code IN ('QUANTITY_VARIANCE', 'TOTAL_CALCULATION_MISMATCH') THEN 'RECONCILIATION_VARIANCE'
    WHEN reason_code = 'CONFIGURATION_ISSUE' THEN 'CONFIGURATION_ISSUE'
    WHEN reason_code = 'SOURCE_DATA_ISSUE' THEN 'SOURCE_DATA_ISSUE'
    WHEN reason_code = 'MANUAL_REVIEW_REQUIRED' THEN 'MANUAL_REVIEW'
    ELSE root_cause_category
END
WHERE root_cause_category IS NULL;

CREATE INDEX IF NOT EXISTS idx_exception_cases_root_cause
    ON recon.exception_cases (tenant_id, recon_view, root_cause_category, reason_code, created_at DESC);

