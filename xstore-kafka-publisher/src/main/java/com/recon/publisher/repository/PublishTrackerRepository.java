package com.recon.publisher.repository;

import com.recon.publisher.domain.PoslogRecord;
import com.recon.publisher.domain.TrackerRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class PublishTrackerRepository {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void ensureTrackerLockColumns() {
        ensureColumn("ALTER TABLE recon.xstore_kafka_publish_tracker ADD (LOCK_OWNER VARCHAR2(128))");
        ensureColumn("ALTER TABLE recon.xstore_kafka_publish_tracker ADD (LOCK_EXPIRES_AT TIMESTAMP(6))");
    }

    private void ensureColumn(String ddl) {
        try {
            jdbcTemplate.execute(ddl);
        } catch (Exception ex) {
            String message = ex.getMessage();
            if (message == null
                    || (!message.contains("ORA-01430")
                    && !message.contains("ORA-00955")
                    && !message.contains("ORA-01442"))) {
                throw ex;
            }
        }
    }

    public void seedPendingRows(int batchSize) {
        String sql = """
                MERGE INTO recon.xstore_kafka_publish_tracker t
                USING (
                    SELECT p.ORGANIZATION_ID, p.RTL_LOC_ID, p.BUSINESS_DATE,
                           p.WKSTN_ID, p.TRANS_SEQ
                    FROM TRN_POSLOG_DATA p
                    LEFT JOIN recon.xstore_kafka_publish_tracker k
                        ON p.ORGANIZATION_ID = k.ORGANIZATION_ID
                        AND p.RTL_LOC_ID = k.RTL_LOC_ID
                        AND p.BUSINESS_DATE = k.BUSINESS_DATE
                        AND p.WKSTN_ID = k.WKSTN_ID
                        AND p.TRANS_SEQ = k.TRANS_SEQ
                    WHERE k.TRANS_SEQ IS NULL
                      AND DBMS_LOB.GETLENGTH(p.POSLOG_BYTES) > 0
                    ORDER BY p.BUSINESS_DATE ASC, p.TRANS_SEQ ASC
                    FETCH FIRST ? ROWS ONLY
                ) s
                ON (t.ORGANIZATION_ID = s.ORGANIZATION_ID
                    AND t.RTL_LOC_ID = s.RTL_LOC_ID
                    AND t.BUSINESS_DATE = s.BUSINESS_DATE
                    AND t.WKSTN_ID = s.WKSTN_ID
                    AND t.TRANS_SEQ = s.TRANS_SEQ)
                WHEN NOT MATCHED THEN INSERT (
                    ORGANIZATION_ID, RTL_LOC_ID, BUSINESS_DATE,
                    WKSTN_ID, TRANS_SEQ, STATUS,
                    RETRY_COUNT, FIRST_ATTEMPTED_AT
                ) VALUES (
                    s.ORGANIZATION_ID, s.RTL_LOC_ID, s.BUSINESS_DATE,
                    s.WKSTN_ID, s.TRANS_SEQ, 'PENDING',
                    0, SYSTIMESTAMP
                )
                """;
        jdbcTemplate.update(sql, batchSize);
    }

    public int releaseStaleClaims(int lockTimeoutMinutes) {
        String sql = """
                UPDATE recon.xstore_kafka_publish_tracker
                SET STATUS = 'PENDING',
                    LOCK_OWNER = NULL,
                    LOCK_EXPIRES_AT = NULL
                WHERE STATUS = 'PROCESSING'
                  AND LOCK_EXPIRES_AT < SYSTIMESTAMP
                  AND LOCK_EXPIRES_AT IS NOT NULL
                """;
        return jdbcTemplate.update(sql);
    }

    public Map<String, Long> countByStatus() {
        String sql = """
                SELECT STATUS, COUNT(*)
                FROM recon.xstore_kafka_publish_tracker
                GROUP BY STATUS
                ORDER BY STATUS
                """;
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbcTemplate.query(sql, (RowCallbackHandler) rs ->
                counts.put(rs.getString(1), rs.getLong(2)));
        return counts;
    }

    public java.sql.Timestamp oldestTimestampForStatus(String status) {
        String sql = """
                SELECT MIN(COALESCE(LAST_ATTEMPTED_AT, FIRST_ATTEMPTED_AT, PUBLISHED_AT))
                FROM recon.xstore_kafka_publish_tracker
                WHERE STATUS = ?
                """;
        return jdbcTemplate.queryForObject(sql, java.sql.Timestamp.class, status);
    }

    @Transactional
    public List<PoslogRecord> claimBatch(String workerId,
                                         int batchSize,
                                         int maxRetries,
                                         int processingLockTimeoutMinutes) {
        String selectSql = """
                SELECT t.ORGANIZATION_ID, t.RTL_LOC_ID, t.BUSINESS_DATE,
                       t.WKSTN_ID, t.TRANS_SEQ, t.ROWID AS TRACKER_ROWID
                FROM recon.xstore_kafka_publish_tracker t
                WHERE t.STATUS IN ('PENDING', 'FAILED')
                  AND t.RETRY_COUNT < ?
                  AND ROWNUM <= ?
                FOR UPDATE SKIP LOCKED
                """;
        List<ClaimedRow> claimed = jdbcTemplate.query(
                selectSql,
                (rs, rn) -> mapClaimedRow(rs),
                maxRetries, batchSize);
        if (claimed.isEmpty()) {
            return List.of();
        }

        String updateSql = """
                UPDATE recon.xstore_kafka_publish_tracker
                SET STATUS = 'PROCESSING',
                    LOCK_OWNER = ?,
                    LOCK_EXPIRES_AT = SYSTIMESTAMP + NUMTODSINTERVAL(?, 'MINUTE'),
                    LAST_ATTEMPTED_AT = SYSTIMESTAMP
                WHERE ROWID = ?
                """;
        for (ClaimedRow row : claimed) {
            jdbcTemplate.update(updateSql,
                    workerId,
                    processingLockTimeoutMinutes,
                    row.trackerRowId());
        }
        return claimed.stream()
                .map(ClaimedRow::record)
                .map(this::loadPoslogRecord)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public void markPublished(PoslogRecord record, String externalId,
                              String txnKey, String txnType,
                              int partition, long offset,
                              String checksum, boolean compressed) {
        String sql = """
                UPDATE recon.xstore_kafka_publish_tracker
                SET STATUS = 'PUBLISHED',
                    EXTERNAL_ID = ?,
                    TRANSACTION_KEY = ?,
                    TRANS_TYPCODE = ?,
                    KAFKA_PARTITION = ?,
                    KAFKA_OFFSET = ?,
                    PAYLOAD_HASH = ?,
                    POSLOG_COMPRESSED = ?,
                    LOCK_OWNER = NULL,
                    LOCK_EXPIRES_AT = NULL,
                    PUBLISHED_AT = SYSTIMESTAMP,
                    LAST_ATTEMPTED_AT = SYSTIMESTAMP,
                    POSLOG_UPDATE_DATE = ?
                WHERE ORGANIZATION_ID = ?
                  AND RTL_LOC_ID = ?
                  AND BUSINESS_DATE = ?
                  AND WKSTN_ID = ?
                  AND TRANS_SEQ = ?
                """;
        jdbcTemplate.update(sql,
                externalId, txnKey, txnType,
                partition, offset, checksum,
                compressed ? "Y" : "N",
                record.getUpdateDate(),
                record.getOrganizationId(), record.getRtlLocId(),
                record.getBusinessDate(), record.getWkstnId(),
                record.getTransSeq());
    }

    public void markFailed(PoslogRecord record, String errorMessage) {
        String sql = """
                UPDATE recon.xstore_kafka_publish_tracker
                SET STATUS = 'FAILED',
                    RETRY_COUNT = RETRY_COUNT + 1,
                    LOCK_OWNER = NULL,
                    LOCK_EXPIRES_AT = NULL,
                    LAST_ATTEMPTED_AT = SYSTIMESTAMP,
                    ERROR_MESSAGE = ?
                WHERE ORGANIZATION_ID = ?
                  AND RTL_LOC_ID = ?
                  AND BUSINESS_DATE = ?
                  AND WKSTN_ID = ?
                  AND TRANS_SEQ = ?
                """;
        jdbcTemplate.update(sql,
                errorMessage != null && errorMessage.length() > 500
                        ? errorMessage.substring(0, 500) : errorMessage,
                record.getOrganizationId(), record.getRtlLocId(),
                record.getBusinessDate(), record.getWkstnId(),
                record.getTransSeq());
    }

    public void markSkipped(PoslogRecord record, String txnType) {
        String sql = """
                UPDATE recon.xstore_kafka_publish_tracker
                SET STATUS = 'SKIPPED',
                    LOCK_OWNER = NULL,
                    LOCK_EXPIRES_AT = NULL,
                    TRANS_TYPCODE = ?,
                    LAST_ATTEMPTED_AT = SYSTIMESTAMP
                WHERE ORGANIZATION_ID = ?
                  AND RTL_LOC_ID = ?
                  AND BUSINESS_DATE = ?
                  AND WKSTN_ID = ?
                  AND TRANS_SEQ = ?
                """;
        jdbcTemplate.update(sql,
                txnType,
                record.getOrganizationId(), record.getRtlLocId(),
                record.getBusinessDate(), record.getWkstnId(),
                record.getTransSeq());
    }

    public void markDlq(PoslogRecord record) {
        String sql = """
                UPDATE recon.xstore_kafka_publish_tracker
                SET STATUS = 'DLQ',
                    LOCK_OWNER = NULL,
                    LOCK_EXPIRES_AT = NULL,
                    LAST_ATTEMPTED_AT = SYSTIMESTAMP
                WHERE ORGANIZATION_ID = ?
                  AND RTL_LOC_ID = ?
                  AND BUSINESS_DATE = ?
                  AND WKSTN_ID = ?
                  AND TRANS_SEQ = ?
                """;
        jdbcTemplate.update(sql,
                record.getOrganizationId(), record.getRtlLocId(),
                record.getBusinessDate(), record.getWkstnId(),
                record.getTransSeq());
    }

    public int getRetryCount(PoslogRecord record) {
        String sql = """
                SELECT RETRY_COUNT
                FROM recon.xstore_kafka_publish_tracker
                WHERE ORGANIZATION_ID = ?
                  AND RTL_LOC_ID = ?
                  AND BUSINESS_DATE = ?
                  AND WKSTN_ID = ?
                  AND TRANS_SEQ = ?
                """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class,
                record.getOrganizationId(), record.getRtlLocId(),
                record.getBusinessDate(), record.getWkstnId(),
                record.getTransSeq());
        return count != null ? count : 0;
    }

    public TrackerRow find(PoslogRecord record) {
        try {
            String sql = """
                    SELECT STATUS, RETRY_COUNT,
                           POSLOG_UPDATE_DATE, PAYLOAD_HASH
                    FROM recon.xstore_kafka_publish_tracker
                    WHERE ORGANIZATION_ID = ?
                      AND RTL_LOC_ID = ?
                      AND BUSINESS_DATE = ?
                      AND WKSTN_ID = ?
                      AND TRANS_SEQ = ?
                    """;
            return jdbcTemplate.queryForObject(sql,
                    (rs, rn) -> TrackerRow.builder()
                            .status(rs.getString("STATUS"))
                            .retryCount(rs.getInt("RETRY_COUNT"))
                            .poslogUpdateDate(rs.getTimestamp("POSLOG_UPDATE_DATE"))
                            .payloadHash(rs.getString("PAYLOAD_HASH"))
                            .build(),
                    record.getOrganizationId(), record.getRtlLocId(),
                    record.getBusinessDate(), record.getWkstnId(),
                    record.getTransSeq());
        } catch (Exception e) {
            return null;
        }
    }

    private ClaimedRow mapClaimedRow(ResultSet rs) throws SQLException {
        PoslogRecord record = PoslogRecord.builder()
                .organizationId(rs.getLong("ORGANIZATION_ID"))
                .rtlLocId(rs.getLong("RTL_LOC_ID"))
                .businessDate(rs.getDate("BUSINESS_DATE").toLocalDate())
                .wkstnId(rs.getLong("WKSTN_ID"))
                .transSeq(rs.getLong("TRANS_SEQ"))
                .build();
        return new ClaimedRow(record, rs.getString("TRACKER_ROWID"));
    }

    private PoslogRecord loadPoslogRecord(PoslogRecord trackerRecord) {
        String sql = """
                SELECT ORGANIZATION_ID, RTL_LOC_ID, BUSINESS_DATE,
                       WKSTN_ID, TRANS_SEQ, POSLOG_BYTES,
                       CREATE_DATE, UPDATE_DATE
                FROM TRN_POSLOG_DATA
                WHERE ORGANIZATION_ID = ?
                  AND RTL_LOC_ID = ?
                  AND BUSINESS_DATE = ?
                  AND WKSTN_ID = ?
                  AND TRANS_SEQ = ?
                  AND DBMS_LOB.GETLENGTH(POSLOG_BYTES) > 0
                """;
        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    (rs, rn) -> PoslogRecord.builder()
                            .organizationId(rs.getLong("ORGANIZATION_ID"))
                            .rtlLocId(rs.getLong("RTL_LOC_ID"))
                            .businessDate(rs.getDate("BUSINESS_DATE").toLocalDate())
                            .wkstnId(rs.getLong("WKSTN_ID"))
                            .transSeq(rs.getLong("TRANS_SEQ"))
                            .poslogBytes(rs.getBytes("POSLOG_BYTES"))
                            .createDate(rs.getTimestamp("CREATE_DATE"))
                            .updateDate(rs.getTimestamp("UPDATE_DATE"))
                            .build(),
                    trackerRecord.getOrganizationId(),
                    trackerRecord.getRtlLocId(),
                    java.sql.Date.valueOf(trackerRecord.getBusinessDate()),
                    trackerRecord.getWkstnId(),
                    trackerRecord.getTransSeq());
        } catch (Exception ex) {
            return null;
        }
    }

    private record ClaimedRow(PoslogRecord record, String trackerRowId) {
    }
}
