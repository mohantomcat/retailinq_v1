package com.recon.rms.repository;

import com.recon.integration.recon.TransactionDomain;
import com.recon.integration.recon.TransactionDomainResolver;
import com.recon.rms.config.PollerConfig;
import com.recon.rms.domain.RmsRawRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@Slf4j
public class RmsRepository {

    private static final List<Integer> INVENTORY_TRANSACTION_TYPES =
            TransactionDomainResolver.supportedTransactionTypes().stream()
                    .filter(type -> TransactionDomainResolver.resolve(type) == TransactionDomain.INVENTORY)
                    .sorted()
                    .toList();
    private static final String INVENTORY_TRANSACTION_TYPE_PLACEHOLDERS =
            INVENTORY_TRANSACTION_TYPES.stream()
                    .map(ignored -> "?")
                    .collect(Collectors.joining(", "));

    private final JdbcTemplate jdbcTemplate;
    private final PollerConfig config;

    public RmsRepository(
            @Qualifier("rmsJdbcTemplate")
            JdbcTemplate jdbcTemplate,
            PollerConfig config) {
        this.jdbcTemplate = jdbcTemplate;
        this.config = config;
    }

    public List<RmsRawRow> findRawRows(Timestamp fromTimestamp,
                                       String fromExternalId,
                                       long fromId,
                                       int pageSize) {
        String safeExtId = fromExternalId != null ? fromExternalId : "";
        List<Object> args = new ArrayList<>(INVENTORY_TRANSACTION_TYPES.size() + 6);
        args.addAll(INVENTORY_TRANSACTION_TYPES);
        args.add(fromTimestamp);
        args.add(fromTimestamp);
        args.add(safeExtId);
        args.add(safeExtId);
        args.add(fromId);
        args.add(pageSize);
        return jdbcTemplate.query(
                buildFindRawRowsSql(),
                new RmsRawRowMapper(),
                args.toArray());
    }

    private String buildFindRawRowsSql() {
        String sourceView = sanitizeIdentifier(config.getTransactionView());
        return """
                SELECT
                    rt.ID, rt.EXTERNAL_ID, rt.REQUEST_ID, rt.STORE_ID,
                    rt.TRANSACTION_DATE_TIME, rt.UPDATE_DATE_TIME,
                    rt.TYPE, rt.SOURCE_TYPE, rt.PROCESSING_STATUS,
                    rt.ITEM_ID, rt.QUANTITY, rt.UNIT_OF_MEASURE,
                    rt.TRANSACTION_EXTENDED_ID, rt.UIN, rt.EPC,
                    rt.DROP_SHIP, rt.REASON
                FROM %s rt
                WHERE rt.TYPE IN (%s)
                  AND (
                      rt.UPDATE_DATE_TIME > ?
                      OR (
                          rt.UPDATE_DATE_TIME = ?
                          AND (
                              rt.EXTERNAL_ID > ?
                              OR (rt.EXTERNAL_ID = ? AND rt.ID > ?)
                          )
                      )
                  )
                ORDER BY rt.UPDATE_DATE_TIME ASC,
                         rt.EXTERNAL_ID ASC,
                         rt.ID ASC
                FETCH FIRST ? ROWS ONLY
                """.formatted(sourceView, INVENTORY_TRANSACTION_TYPE_PLACEHOLDERS);
    }

    private String sanitizeIdentifier(String value) {
        String identifier = value == null || value.isBlank() ? "RMS_TRANSACTION_V" : value.trim();
        if (!identifier.matches("[A-Za-z0-9_.$]+")) {
            throw new IllegalArgumentException("Invalid RMS transaction view name: " + identifier);
        }
        return identifier;
    }

    private static class RmsRawRowMapper implements RowMapper<RmsRawRow> {
        @Override
        public RmsRawRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return RmsRawRow.builder()
                    .id(rs.getLong("ID"))
                    .externalId(rs.getString("EXTERNAL_ID"))
                    .requestId(rs.getLong("REQUEST_ID"))
                    .storeId(rs.getString("STORE_ID"))
                    .transactionDateTime(rs.getTimestamp("TRANSACTION_DATE_TIME"))
                    .updateDateTime(rs.getTimestamp("UPDATE_DATE_TIME"))
                    .type(rs.getInt("TYPE"))
                    .sourceType(rs.getInt("SOURCE_TYPE"))
                    .processingStatus(rs.getInt("PROCESSING_STATUS"))
                    .itemId(rs.getString("ITEM_ID"))
                    .quantity(rs.getBigDecimal("QUANTITY"))
                    .unitOfMeasure(rs.getString("UNIT_OF_MEASURE"))
                    .transactionExtendedId(rs.getString("TRANSACTION_EXTENDED_ID"))
                    .uin(rs.getString("UIN"))
                    .epc(rs.getString("EPC"))
                    .dropShip(rs.getString("DROP_SHIP"))
                    .reason(rs.getString("REASON"))
                    .build();
        }
    }
}
