WITH tenant_jobs AS (
    SELECT
        tc.tenant_id,
        COALESCE(NULLIF(tc.timezone, ''), 'UTC') AS job_timezone,
        (
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD'), 1, 8) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD'), 9, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD'), 13, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD'), 17, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD'), 21, 12)
        )::uuid AS job_id,
        (
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|1'), 1, 8) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|1'), 9, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|1'), 13, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|1'), 17, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|1'), 21, 12)
        )::uuid AS step_1_id,
        (
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|2'), 1, 8) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|2'), 9, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|2'), 13, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|2'), 17, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|2'), 21, 12)
        )::uuid AS step_2_id,
        (
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|3'), 1, 8) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|3'), 9, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|3'), 13, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|3'), 17, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|3'), 21, 12)
        )::uuid AS step_3_id
    FROM recon.tenant_config tc
),
jobs_to_seed AS (
    SELECT
        tj.tenant_id,
        tj.job_timezone,
        tj.job_id,
        tj.step_1_id,
        tj.step_2_id,
        tj.step_3_id
    FROM tenant_jobs tj
    WHERE NOT EXISTS (
        SELECT 1
        FROM recon.recon_job_definition jd
        WHERE jd.tenant_id = tj.tenant_id
          AND lower(jd.job_name) = lower('SIM vs RMS EOD')
    )
)
INSERT INTO recon.recon_job_definition (
    id,
    tenant_id,
    job_name,
    recon_view,
    cron_expression,
    job_timezone,
    window_type,
    end_of_day_local_time,
    business_date_offset_days,
    max_retry_attempts,
    retry_delay_minutes,
    allow_concurrent_runs,
    enabled,
    notify_on_success,
    notify_on_failure,
    next_scheduled_at,
    created_by,
    updated_by,
    created_at,
    updated_at
)
SELECT
    js.job_id,
    js.tenant_id,
    'SIM vs RMS EOD',
    'SIM_RMS',
    '0 0 0 * * *',
    js.job_timezone,
    'END_OF_DAY',
    '23:55',
    0,
    1,
    15,
    false,
    true,
    false,
    true,
    timezone(
        current_setting('TIMEZONE'),
        (
            (date_trunc('day', now() AT TIME ZONE js.job_timezone) + interval '1 day')
            AT TIME ZONE js.job_timezone
        )
    ),
    'system',
    'system',
    now(),
    now()
FROM jobs_to_seed js
ON CONFLICT DO NOTHING;

WITH tenant_jobs AS (
    SELECT
        tc.tenant_id,
        (
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD'), 1, 8) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD'), 9, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD'), 13, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD'), 17, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD'), 21, 12)
        )::uuid AS job_id,
        (
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|1'), 1, 8) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|1'), 9, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|1'), 13, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|1'), 17, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|1'), 21, 12)
        )::uuid AS step_1_id,
        (
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|2'), 1, 8) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|2'), 9, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|2'), 13, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|2'), 17, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|2'), 21, 12)
        )::uuid AS step_2_id,
        (
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|3'), 1, 8) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|3'), 9, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|3'), 13, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|3'), 17, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|3'), 21, 12)
        )::uuid AS step_3_id
    FROM recon.tenant_config tc
)
INSERT INTO recon.recon_job_step_definition (
    id,
    job_definition_id,
    step_order,
    step_label,
    step_type,
    module_id,
    action_key,
    depends_on_step_id,
    settle_delay_seconds,
    step_config,
    created_at,
    updated_at
)
SELECT
    tj.step_1_id,
    tj.job_id,
    1,
    'Poll SIM inventory transactions',
    'OPERATIONS_ACTION',
    'sim-db-connector',
    'poll',
    NULL,
    0,
    NULL,
    now(),
    now()
FROM tenant_jobs tj
JOIN recon.recon_job_definition jd
  ON jd.id = tj.job_id
ON CONFLICT DO NOTHING;

WITH tenant_jobs AS (
    SELECT
        tc.tenant_id,
        (
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD'), 1, 8) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD'), 9, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD'), 13, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD'), 17, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD'), 21, 12)
        )::uuid AS job_id,
        (
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|1'), 1, 8) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|1'), 9, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|1'), 13, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|1'), 17, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|1'), 21, 12)
        )::uuid AS step_1_id,
        (
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|2'), 1, 8) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|2'), 9, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|2'), 13, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|2'), 17, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|2'), 21, 12)
        )::uuid AS step_2_id
    FROM recon.tenant_config tc
)
INSERT INTO recon.recon_job_step_definition (
    id,
    job_definition_id,
    step_order,
    step_label,
    step_type,
    module_id,
    action_key,
    depends_on_step_id,
    settle_delay_seconds,
    step_config,
    created_at,
    updated_at
)
SELECT
    tj.step_2_id,
    tj.job_id,
    2,
    'Poll RMS inventory transactions',
    'OPERATIONS_ACTION',
    'rms-db-connector',
    'poll',
    tj.step_1_id,
    60,
    NULL,
    now(),
    now()
FROM tenant_jobs tj
JOIN recon.recon_job_definition jd
  ON jd.id = tj.job_id
ON CONFLICT DO NOTHING;

WITH tenant_jobs AS (
    SELECT
        tc.tenant_id,
        (
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD'), 1, 8) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD'), 9, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD'), 13, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD'), 17, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD'), 21, 12)
        )::uuid AS job_id,
        (
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|2'), 1, 8) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|2'), 9, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|2'), 13, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|2'), 17, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|2'), 21, 12)
        )::uuid AS step_2_id,
        (
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|3'), 1, 8) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|3'), 9, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|3'), 13, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|3'), 17, 4) || '-' ||
            substr(md5(tc.tenant_id || '|RECON_JOB|SIM_RMS_EOD|STEP|3'), 21, 12)
        )::uuid AS step_3_id
    FROM recon.tenant_config tc
)
INSERT INTO recon.recon_job_step_definition (
    id,
    job_definition_id,
    step_order,
    step_label,
    step_type,
    module_id,
    action_key,
    depends_on_step_id,
    settle_delay_seconds,
    step_config,
    created_at,
    updated_at
)
SELECT
    tj.step_3_id,
    tj.job_id,
    3,
    'Capture reconciliation summary',
    'RECON_SUMMARY_SNAPSHOT',
    NULL,
    NULL,
    tj.step_2_id,
    30,
    NULL,
    now(),
    now()
FROM tenant_jobs tj
JOIN recon.recon_job_definition jd
  ON jd.id = tj.job_id
ON CONFLICT DO NOTHING;
