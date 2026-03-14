package com.recon.cloud.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CloudTransactionEvent {
    @Builder.Default
    private int schemaVersion = 1;
    private String eventType;
    private String eventId;
    private String source;
    private String publishedAt;
    private String tenantId;
    private String tenantTimezone;
    private String externalId;
    private String transactionKey;
    private Long requestId;
    private String storeId;
    private String businessDate;
    private String transactionDateTime;
    private String updateDateTime;
    private Integer transactionType;
    private String transactionTypeDesc;
    private Integer processingStatus;
    private String processingStatusDesc;
    private List<CloudLineItem> lineItems;
    private int lineItemCount;
    private BigDecimal totalQuantity;
    private boolean duplicateFlag;
    private int duplicatePostingCount;
    private String checksum;
}
