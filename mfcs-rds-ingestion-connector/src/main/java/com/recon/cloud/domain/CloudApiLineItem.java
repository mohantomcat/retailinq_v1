package com.recon.cloud.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudApiLineItem {
    @JsonAlias({"id", "ID"})
    private Long id;
    @JsonAlias({"itemId", "item_id", "ITEM_ID"})
    private String itemId;
    @JsonAlias({"quantity", "QUANTITY"})
    private BigDecimal quantity;
    @JsonAlias({"unitOfMeasure", "unit_of_measure", "UNIT_OF_MEASURE"})
    private String unitOfMeasure;
    @JsonAlias({"type", "TYPE"})
    private Integer type;
    @JsonAlias({"processingStatus", "processing_status", "PROCESSING_STATUS"})
    private Integer processingStatus;
    @JsonAlias({"transactionExtendedId", "transaction_extended_id", "TRANSACTION_EXTENDED_ID"})
    private String transactionExtendedId;
}
