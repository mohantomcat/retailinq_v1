package com.recon.publisher.repository;

import com.recon.publisher.domain.PoslogRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PoslogRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String FIND_UNPUBLISHED = """
            SELECT p.ORGANIZATION_ID, p.RTL_LOC_ID, p.BUSINESS_DATE,
                   p.WKSTN_ID, p.TRANS_SEQ, p.POSLOG_BYTES,
                   p.CREATE_DATE, p.UPDATE_DATE
            FROM TRN_POSLOG_DATA p
            LEFT JOIN recon.xstore_kafka_publish_tracker k
                ON p.ORGANIZATION_ID = k.ORGANIZATION_ID
                AND p.RTL_LOC_ID     = k.RTL_LOC_ID
                AND p.BUSINESS_DATE  = k.BUSINESS_DATE
                AND p.WKSTN_ID       = k.WKSTN_ID
                AND p.TRANS_SEQ      = k.TRANS_SEQ
            WHERE (k.TRANS_SEQ IS NULL
                   OR k.STATUS IN ('PENDING', 'FAILED'))
            AND DBMS_LOB.GETLENGTH(p.POSLOG_BYTES) > 0
            AND (k.RETRY_COUNT IS NULL OR k.RETRY_COUNT < ?)
            ORDER BY p.BUSINESS_DATE ASC, p.TRANS_SEQ ASC
            FETCH FIRST ? ROWS ONLY
            """;

    public List<PoslogRecord> findUnpublished(int batchSize, int maxRetries) {
        return jdbcTemplate.query(
                FIND_UNPUBLISHED,
                new PoslogRecordMapper(),
                maxRetries, batchSize);
    }

    private static class PoslogRecordMapper
            implements RowMapper<PoslogRecord> {
        @Override
        public PoslogRecord mapRow(ResultSet rs, int rowNum)
                throws SQLException {
            return PoslogRecord.builder()
                    .organizationId(rs.getLong("ORGANIZATION_ID"))
                    .rtlLocId(rs.getLong("RTL_LOC_ID"))
                    .businessDate(rs.getDate("BUSINESS_DATE").toLocalDate())
                    .wkstnId(rs.getLong("WKSTN_ID"))
                    .transSeq(rs.getLong("TRANS_SEQ"))
                    .poslogBytes(rs.getBytes("POSLOG_BYTES"))
                    .createDate(rs.getTimestamp("CREATE_DATE"))
                    .updateDate(rs.getTimestamp("UPDATE_DATE"))
                    //.transTypcode(rs.getString("TRANS_TYPCODE"))
                    .build();
        }
    }
}