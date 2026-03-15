package com.recon.xocs.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XocsStagedTransaction {
    private Long id;
    private String tenantId;
    private String sourceName;
    private long organizationId;
    private long rtlLocId;
    private LocalDate businessDate;
    private long wkstnId;
    private long transSeq;
    private String externalId;
    private String transactionKey;
    private Timestamp beginDatetime;
    private Timestamp endDatetime;
    private Long sessionId;
    private Long operatorPartyId;
    private String transTypcode;
    private String transStatcode;
    private BigDecimal transTotal;
    private BigDecimal transSubtotal;
    private BigDecimal transTaxtotal;
    private BigDecimal transRoundtotal;
    private Integer lineCount;
    private Integer distinctItemCount;
    private BigDecimal totalItemQty;
    private BigDecimal sumExtendedAmt;
    private BigDecimal sumNetAmt;
    private BigDecimal sumGrossAmt;
    private Timestamp sourceUpdateDate;
    private Long rawPayloadId;
    private String ingestionStatus;
    private int retryCount;
    private String lastErrorMessage;
    @Builder.Default
    private List<XocsStagedLine> lineItems = new ArrayList<>();
}
