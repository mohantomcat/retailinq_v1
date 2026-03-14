package com.recon.poller.domain;

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
public class SimTransactionEvent {

    @Builder.Default
    private int schemaVersion = 1;  // FIXED: added @Builder.Default
    private String eventType;
    private String eventId;
    private String source;
    private String publishedAt;     // always UTC ISO-8601

    // Multi-tenant fields
    private String tenantId;
    private String tenantTimezone;  // for display only

    // Identity
    private String externalId;
    private String transactionKey;
    private Long requestId;
    private String storeId;
    private String businessDate;    // UTC date yyyy-MM-dd

    // Transaction details — all timestamps UTC ISO-8601
    private String transactionDateTime;
    private String updateDateTime;
    private Integer transactionType;
    private String transactionTypeDesc;

    // Processing state
    private Integer processingStatus;
    private String processingStatusDesc;

    // Line items
    private List<SiocsLineItem> lineItems;
    private int lineItemCount;
    private BigDecimal totalQuantity;

    // Duplicate detection
    private boolean duplicateFlag;
    private int duplicatePostingCount;

    // Reconciliation
    private String checksum;
}