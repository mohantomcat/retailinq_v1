package com.recon.rms.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RmsTransactionRow {
    private String externalId;
    private Long requestId;
    private String storeId;
    private Timestamp transactionDateTime;
    private Timestamp updateDateTime;
    private Integer type;
    private Integer processingStatus;
    private List<RmsLineItem> lineItems;
    private int lineItemCount;
    private BigDecimal totalQuantity;
    private int postingCount;         // distinct TRANSACTION_EXTENDED_ID count
    private boolean duplicateFlag;    // postingCount > 1
}
