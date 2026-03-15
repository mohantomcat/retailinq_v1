package com.recon.cloud.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloudStagedTransaction {
    private String externalId;
    private String sourceRecordKey;
    private Long firstRowId;
    private Long requestId;
    private String storeId;
    private Timestamp transactionDateTime;
    private Timestamp updateDateTime;
    private Integer type;
    private Integer processingStatus;
    @Builder.Default
    private List<CloudLineItem> lineItems = new ArrayList<>();
    private int lineItemCount;
    private BigDecimal totalQuantity;
    private int postingCount;
    private boolean duplicateFlag;
}
