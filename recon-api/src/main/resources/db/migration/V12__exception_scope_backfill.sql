UPDATE recon.exception_cases
SET store_id = COALESCE(
        store_id,
        COALESCE(NULLIF(ltrim(substring(split_part(transaction_key, '|', 2) from 1 for 5), '0'), ''), '0')
    ),
    wkstn_id = COALESCE(
        wkstn_id,
        COALESCE(NULLIF(ltrim(substring(split_part(transaction_key, '|', 2) from 6 for 3), '0'), ''), '0')
    ),
    business_date = COALESCE(
        business_date,
        to_date(substring(split_part(transaction_key, '|', 2) from 15 for 8), 'YYYYMMDD')
    )
WHERE split_part(transaction_key, '|', 2) ~ '^[0-9]{22}$'
  AND (store_id IS NULL OR wkstn_id IS NULL OR business_date IS NULL);

UPDATE recon.exception_cases
SET store_id = COALESCE(
        store_id,
        COALESCE(NULLIF(ltrim(split_part(transaction_key, '|', 2), '0'), ''), '0')
    ),
    wkstn_id = COALESCE(
        wkstn_id,
        COALESCE(NULLIF(ltrim(split_part(transaction_key, '|', 4), '0'), ''), '0')
    ),
    business_date = COALESCE(
        business_date,
        CASE
            WHEN split_part(transaction_key, '|', 3) ~ '^[0-9]{8}$'
                THEN to_date(split_part(transaction_key, '|', 3), 'YYYYMMDD')
            WHEN split_part(transaction_key, '|', 3) ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
                THEN split_part(transaction_key, '|', 3)::date
            ELSE NULL
        END
    )
WHERE array_length(string_to_array(transaction_key, '|'), 1) >= 5
  AND (store_id IS NULL OR wkstn_id IS NULL OR business_date IS NULL);
