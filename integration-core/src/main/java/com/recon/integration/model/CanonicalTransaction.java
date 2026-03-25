package com.recon.integration.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CanonicalTransaction {

    private String transactionId;
    private String transactionType;
    private String transactionSubtype;
    private String documentId;
    private String documentLineId;
    private String referenceDocumentId;
    private String businessDate;
    private String sourceSystem;
    private String targetSystem;
    private String sourceTimestamp;
    private String targetTimestamp;
    private String transactionStatus;
    private String locationFrom;
    private String locationTo;
    private String locationFromType;
    private String locationToType;
    private String supplierId;
    private String customerId;
    private String storeId;
    private String currency;
    private BigDecimal totalQuantity;
    private BigDecimal totalAmount;
    private BigDecimal totalCost;
    private Integer lineCount;
    private List<CanonicalTransactionLine> lineDetails;
    private Map<String, String> referenceAttributes;
}
