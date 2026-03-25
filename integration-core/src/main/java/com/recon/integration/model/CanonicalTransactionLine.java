package com.recon.integration.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CanonicalTransactionLine {

    private String lineId;
    private String itemId;
    private String itemDescription;
    private String unitOfMeasure;
    private BigDecimal quantity;
    private BigDecimal amount;
    private BigDecimal cost;
    private String lineStatus;
    private String lineType;
    private Map<String, String> attributes;
}
