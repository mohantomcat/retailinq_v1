package com.recon.poller.repository;

import com.recon.poller.domain.SiocsRawRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
@Slf4j
public class SiocsRepository {

    private final JdbcTemplate jdbcTemplate;

    // Explicit constructor — required for @Qualifier to work
    public SiocsRepository(
            @Qualifier("siocsJdbcTemplate")
            JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String FIND_RAW_ROWS = """
            SELECT
                pt.ID, pt.EXTERNAL_ID, pt.REQUEST_ID, pt.STORE_ID,
                pt.TRANSACTION_DATE_TIME, pt.UPDATE_DATE_TIME,
                pt.TYPE, pt.SOURCE_TYPE, pt.PROCESSING_STATUS,
                pt.ITEM_ID, pt.QUANTITY, pt.UNIT_OF_MEASURE,
                pt.TRANSACTION_EXTENDED_ID, pt.UIN, pt.EPC,
                pt.DROP_SHIP, pt.REASON
            FROM POS_TRANSACTION pt
            WHERE pt.SOURCE_TYPE = 2
              AND pt.TYPE IN (1, 2, 3, 4)
              AND (
                  pt.UPDATE_DATE_TIME > ?
                  OR (
                      pt.UPDATE_DATE_TIME = ?
                      AND (
                          pt.EXTERNAL_ID > ?
                          OR (pt.EXTERNAL_ID = ? AND pt.ID > ?)
                      )
                  )
              )
            ORDER BY pt.UPDATE_DATE_TIME ASC,
                     pt.EXTERNAL_ID ASC,
                     pt.ID ASC
            FETCH FIRST ? ROWS ONLY
            """;

    public List<SiocsRawRow> findRawRows(Timestamp fromTimestamp,
                                         String fromExternalId,
                                         long fromId,
                                         int pageSize) {
        String safeExtId = fromExternalId != null
                ? fromExternalId : "";
        return jdbcTemplate.query(
                FIND_RAW_ROWS,
                new SiocsRawRowMapper(),
                fromTimestamp, fromTimestamp,
                safeExtId, safeExtId, fromId, pageSize);
    }

    private static class SiocsRawRowMapper
            implements RowMapper<SiocsRawRow> {
        @Override
        public SiocsRawRow mapRow(ResultSet rs, int rowNum)
                throws SQLException {
            return SiocsRawRow.builder()
                    .id(rs.getLong("ID"))
                    .externalId(rs.getString("EXTERNAL_ID"))
                    .requestId(rs.getLong("REQUEST_ID"))
                    .storeId(rs.getString("STORE_ID"))
                    .transactionDateTime(
                            rs.getTimestamp("TRANSACTION_DATE_TIME"))
                    .updateDateTime(
                            rs.getTimestamp("UPDATE_DATE_TIME"))
                    .type(rs.getInt("TYPE"))
                    .sourceType(rs.getInt("SOURCE_TYPE"))
                    .processingStatus(rs.getInt("PROCESSING_STATUS"))
                    .itemId(rs.getString("ITEM_ID"))
                    .quantity(rs.getBigDecimal("QUANTITY"))
                    .unitOfMeasure(rs.getString("UNIT_OF_MEASURE"))
                    .transactionExtendedId(
                            rs.getString("TRANSACTION_EXTENDED_ID"))
                    .uin(rs.getString("UIN"))
                    .epc(rs.getString("EPC"))
                    .dropShip(rs.getString("DROP_SHIP"))
                    .reason(rs.getString("REASON"))
                    .build();
        }
    }
}
