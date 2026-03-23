package com.recon.cloud.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloudIngestionTransactionRow {
    private Long id;
    private String dedupKey;
    private String tenantId;
    private String sourceName;
    private String sourceRecordKey;
    private String sourceCursor;
    private String externalId;
    private String transactionExtendedId;
    private Long requestId;
    private String storeId;
    private Timestamp transactionDateTime;
    private Timestamp updateDateTime;
    private Integer type;
    private Integer processingStatus;
    private Long lineId;
    private String itemId;
    private BigDecimal quantity;
    private String unitOfMeasure;
    private Long rawPayloadId;
    private String ingestionStatus;
    private int retryCount;
    private String lastErrorMessage;
}
