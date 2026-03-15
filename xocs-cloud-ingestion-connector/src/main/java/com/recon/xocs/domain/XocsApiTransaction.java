package com.recon.xocs.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class XocsApiTransaction {
    @JsonAlias({"id", "ID"})
    private Long id;

    @JsonAlias({"sourceRecordKey", "source_record_key", "SOURCE_RECORD_KEY"})
    private String sourceRecordKey;

    @JsonAlias({"organizationId", "organization_id", "ORGANIZATION_ID"})
    private Long organizationId;

    @JsonAlias({"rtlLocId", "rtl_loc_id", "RTL_LOC_ID", "storeId", "store_id", "STORE_ID"})
    private Long rtlLocId;

    @JsonAlias({"businessDate", "business_date", "BUSINESS_DATE"})
    private LocalDate businessDate;

    @JsonAlias({"wkstnId", "wkstn_id", "WKSTN_ID"})
    private Long wkstnId;

    @JsonAlias({"transSeq", "trans_seq", "TRANS_SEQ"})
    private Long transSeq;

    @JsonAlias({"externalId", "external_id", "EXTERNAL_ID"})
    private String externalId;

    @JsonAlias({"transactionKey", "transaction_key", "TRANSACTION_KEY"})
    private String transactionKey;

    @JsonAlias({"beginDatetime", "begin_datetime", "BEGIN_DATETIME"})
    private Instant beginDatetime;

    @JsonAlias({"endDatetime", "end_datetime", "END_DATETIME"})
    private Instant endDatetime;

    @JsonAlias({"sessionId", "session_id", "SESSION_ID"})
    private Long sessionId;

    @JsonAlias({"operatorPartyId", "operator_party_id", "OPERATOR_PARTY_ID"})
    private Long operatorPartyId;

    @JsonAlias({"transTypcode", "trans_typcode", "TRANS_TYPCODE"})
    private String transTypcode;

    @JsonAlias({"transStatcode", "trans_statcode", "TRANS_STATCODE"})
    private String transStatcode;

    @JsonAlias({"transTotal", "trans_total", "TRANS_TOTAL", "total", "TOTAL"})
    private BigDecimal transTotal;

    @JsonAlias({"transSubtotal", "trans_subtotal", "TRANS_SUBTOTAL", "subtotal", "SUBTOTAL"})
    private BigDecimal transSubtotal;

    @JsonAlias({"transTaxtotal", "trans_taxtotal", "TRANS_TAXTOTAL", "taxtotal", "TAXTOTAL"})
    private BigDecimal transTaxtotal;

    @JsonAlias({"transRoundtotal", "trans_roundtotal", "TRANS_ROUNDTOTAL", "roundtotal", "ROUNDTOTAL"})
    private BigDecimal transRoundtotal;

    @JsonAlias({"lineCount", "line_count", "LINE_COUNT"})
    private Integer lineCount;

    @JsonAlias({"distinctItemCount", "distinct_item_count", "DISTINCT_ITEM_COUNT"})
    private Integer distinctItemCount;

    @JsonAlias({"totalItemQty", "total_item_qty", "TOTAL_ITEM_QTY"})
    private BigDecimal totalItemQty;

    @JsonAlias({"sumExtendedAmt", "sum_extended_amt", "SUM_EXTENDED_AMT"})
    private BigDecimal sumExtendedAmt;

    @JsonAlias({"sumNetAmt", "sum_net_amt", "SUM_NET_AMT"})
    private BigDecimal sumNetAmt;

    @JsonAlias({"sumGrossAmt", "sum_gross_amt", "SUM_GROSS_AMT"})
    private BigDecimal sumGrossAmt;

    @JsonAlias({"transUpdateDate", "trans_update_date", "TRANS_UPDATE_DATE", "updateDateTime", "update_date_time", "UPDATE_DATE_TIME"})
    private Instant updateDateTime;

    @Builder.Default
    private List<XocsApiLineItem> lineItems = new ArrayList<>();
}
