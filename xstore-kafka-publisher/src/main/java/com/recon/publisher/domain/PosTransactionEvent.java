package com.recon.publisher.domain;

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
public class PosTransactionEvent {

    @Builder.Default
    private int schemaVersion = 1;  // FIXED: added @Builder.Default
    private String eventType;
    private String eventId;
    private String source;
    private String publishedAt;     // always UTC ISO-8601

    // Multi-tenant fields
    private String tenantId;
    private String tenantTimezone;  // for display conversion only

    // Identity
    private long organizationId;
    private String storeId;
    private String businessDate;    // UTC date yyyy-MM-dd
    private long wkstnId;
    private long transSeq;
    private String externalId;
    private String transactionKey;

    // Transaction details
    private String transactionType;
    private String beginDatetime;   // always UTC ISO-8601
    private String endDatetime;     // always UTC ISO-8601
    private String operatorId;
    private BigDecimal totalAmount;

    // Line items
    private List<LineItem> lineItems;

    // Reconciliation fields
    private String checksum;
    private boolean compressed;
    private boolean clockDriftDetected;
}