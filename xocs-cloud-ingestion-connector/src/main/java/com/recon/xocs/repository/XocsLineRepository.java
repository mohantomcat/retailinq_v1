package com.recon.xocs.repository;

import com.recon.xocs.domain.XocsApiLineItem;
import com.recon.xocs.domain.XocsStagedLine;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class XocsLineRepository {

    private final JdbcTemplate jdbcTemplate;

    public void replaceLines(long transactionId,
                             String transactionKey,
                             long organizationId,
                             long rtlLocId,
                             java.time.LocalDate businessDate,
                             long wkstnId,
                             long transSeq,
                             Instant sourceUpdateDate,
                             List<XocsApiLineItem> lineItems) {
        jdbcTemplate.update("DELETE FROM recon.xocs_ingestion_line WHERE transaction_id = ?", transactionId);
        if (lineItems == null || lineItems.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate("""
                INSERT INTO recon.xocs_ingestion_line (
                    transaction_id, organization_id, rtl_loc_id, business_date, wkstn_id, trans_seq,
                    transaction_key, rtrans_lineitm_seq, transaction_line_key, item_id, scanned_item_id,
                    unit_of_measure, return_flag, void_flag, line_business_type, raw_quantity,
                    normalized_quantity, unit_price, raw_extended_amt, raw_net_amt, gross_amt, vat_amt,
                    sale_lineitm_typcode, rtrans_lineitm_typcode, rtrans_lineitm_statcode,
                    inventory_action_code, serial_nbr, source_update_date, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                XocsApiLineItem line = lineItems.get(i);
                ps.setLong(1, transactionId);
                ps.setLong(2, organizationId);
                ps.setLong(3, rtlLocId);
                ps.setObject(4, businessDate);
                ps.setLong(5, wkstnId);
                ps.setLong(6, transSeq);
                ps.setString(7, transactionKey);
                ps.setObject(8, line.getRtransLineitmSeq());
                ps.setString(9, buildTransactionLineKey(transactionKey, line.getRtransLineitmSeq()));
                ps.setString(10, line.getItemId());
                ps.setString(11, line.getScannedItemId());
                ps.setString(12, line.getUnitOfMeasure());
                ps.setObject(13, line.getReturnFlag());
                ps.setObject(14, line.getVoidFlag());
                ps.setString(15, line.getLineBusinessType());
                ps.setBigDecimal(16, line.getRawQuantity());
                ps.setBigDecimal(17, line.getNormalizedQuantity());
                ps.setBigDecimal(18, line.getUnitPrice());
                ps.setBigDecimal(19, line.getRawExtendedAmt());
                ps.setBigDecimal(20, line.getRawNetAmt());
                ps.setBigDecimal(21, line.getGrossAmt());
                ps.setBigDecimal(22, line.getVatAmt());
                ps.setString(23, line.getSaleLineitmTypcode());
                ps.setString(24, line.getRtransLineitmTypcode());
                ps.setString(25, line.getRtransLineitmStatcode());
                ps.setString(26, line.getInventoryActionCode());
                ps.setString(27, line.getSerialNbr());
                ps.setTimestamp(28, toTimestamp(sourceUpdateDate != null ? sourceUpdateDate : line.getLineUpdateDate()));
            }

            @Override
            public int getBatchSize() {
                return lineItems.size();
            }
        });
    }

    public Map<Long, List<XocsStagedLine>> findByTransactionIds(List<Long> transactionIds) {
        if (transactionIds == null || transactionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<XocsStagedLine>> grouped = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT id, transaction_id, organization_id, rtl_loc_id, business_date, wkstn_id, trans_seq,
                       transaction_key, rtrans_lineitm_seq, transaction_line_key, item_id, scanned_item_id,
                       unit_of_measure, return_flag, void_flag, line_business_type, raw_quantity,
                       normalized_quantity, unit_price, raw_extended_amt, raw_net_amt, gross_amt, vat_amt,
                       sale_lineitm_typcode, rtrans_lineitm_typcode, rtrans_lineitm_statcode,
                       inventory_action_code, serial_nbr, source_update_date
                FROM recon.xocs_ingestion_line
                WHERE transaction_id = ANY (?)
                ORDER BY transaction_id, rtrans_lineitm_seq
                """, ps -> ps.setArray(1, ps.getConnection().createArrayOf("bigint", transactionIds.toArray())),
                (RowCallbackHandler) rs -> grouped.computeIfAbsent(rs.getLong("transaction_id"), ignored -> new ArrayList<>())
                        .add(XocsStagedLine.builder()
                                .id(rs.getLong("id"))
                                .transactionId(rs.getLong("transaction_id"))
                                .organizationId(rs.getLong("organization_id"))
                                .rtlLocId(rs.getLong("rtl_loc_id"))
                                .businessDate(rs.getObject("business_date", java.time.LocalDate.class))
                                .wkstnId(rs.getLong("wkstn_id"))
                                .transSeq(rs.getLong("trans_seq"))
                                .transactionKey(rs.getString("transaction_key"))
                                .rtransLineitmSeq(rs.getObject("rtrans_lineitm_seq", Long.class))
                                .transactionLineKey(rs.getString("transaction_line_key"))
                                .itemId(rs.getString("item_id"))
                                .scannedItemId(rs.getString("scanned_item_id"))
                                .unitOfMeasure(rs.getString("unit_of_measure"))
                                .returnFlag(rs.getObject("return_flag", Integer.class))
                                .voidFlag(rs.getObject("void_flag", Integer.class))
                                .lineBusinessType(rs.getString("line_business_type"))
                                .rawQuantity(rs.getBigDecimal("raw_quantity"))
                                .normalizedQuantity(rs.getBigDecimal("normalized_quantity"))
                                .unitPrice(rs.getBigDecimal("unit_price"))
                                .rawExtendedAmt(rs.getBigDecimal("raw_extended_amt"))
                                .rawNetAmt(rs.getBigDecimal("raw_net_amt"))
                                .grossAmt(rs.getBigDecimal("gross_amt"))
                                .vatAmt(rs.getBigDecimal("vat_amt"))
                                .saleLineitmTypcode(rs.getString("sale_lineitm_typcode"))
                                .rtransLineitmTypcode(rs.getString("rtrans_lineitm_typcode"))
                                .rtransLineitmStatcode(rs.getString("rtrans_lineitm_statcode"))
                                .inventoryActionCode(rs.getString("inventory_action_code"))
                                .serialNbr(rs.getString("serial_nbr"))
                                .sourceUpdateDate(rs.getTimestamp("source_update_date"))
                                .build()));
        return grouped;
    }

    private String buildTransactionLineKey(String transactionKey, Long lineSeq) {
        return transactionKey + "|" + (lineSeq == null ? "0" : lineSeq);
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
