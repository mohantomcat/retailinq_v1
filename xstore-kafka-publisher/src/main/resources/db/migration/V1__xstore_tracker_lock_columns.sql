BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE recon.xstore_kafka_publish_tracker ADD (LOCK_OWNER VARCHAR2(128))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1430 THEN
            RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE recon.xstore_kafka_publish_tracker ADD (LOCK_EXPIRES_AT TIMESTAMP(6))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1430 THEN
            RAISE;
        END IF;
END;
/
