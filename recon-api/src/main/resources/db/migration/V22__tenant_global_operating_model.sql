ALTER TABLE IF EXISTS recon.tenant_config
    ADD COLUMN IF NOT EXISTS locale_code VARCHAR(16),
    ADD COLUMN IF NOT EXISTS week_start_day VARCHAR(16),
    ADD COLUMN IF NOT EXISTS business_days VARCHAR(128),
    ADD COLUMN IF NOT EXISTS workday_start_time VARCHAR(8),
    ADD COLUMN IF NOT EXISTS workday_end_time VARCHAR(8),
    ADD COLUMN IF NOT EXISTS holiday_calendar TEXT;

UPDATE recon.tenant_config
SET locale_code = COALESCE(
        locale_code,
        CASE UPPER(COALESCE(country_code, ''))
            WHEN 'IN' THEN 'en-IN'
            WHEN 'US' THEN 'en-US'
            WHEN 'GB' THEN 'en-GB'
            WHEN 'AE' THEN 'ar-AE'
            WHEN 'SA' THEN 'ar-SA'
            WHEN 'EG' THEN 'ar-EG'
            WHEN 'JP' THEN 'ja-JP'
            WHEN 'DE' THEN 'de-DE'
            WHEN 'FR' THEN 'fr-FR'
            WHEN 'ES' THEN 'es-ES'
            WHEN 'BR' THEN 'pt-BR'
            ELSE 'en-US'
        END
    ),
    week_start_day = COALESCE(week_start_day, 'MONDAY'),
    business_days = COALESCE(business_days, 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY'),
    workday_start_time = COALESCE(workday_start_time, '06:00'),
    workday_end_time = COALESCE(workday_end_time, '22:00'),
    holiday_calendar = COALESCE(holiday_calendar, '');
