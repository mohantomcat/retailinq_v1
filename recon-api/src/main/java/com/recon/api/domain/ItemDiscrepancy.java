package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemDiscrepancy {
    private String itemId;
    private String type;
    private String lineType;
    private BigDecimal xstoreQuantity;
    private BigDecimal siocsQuantity;
    private BigDecimal xstoreAmount;
    private BigDecimal siocsAmount;
    private String xstoreUom;
    private String siocsUom;
    private BigDecimal varianceQuantity;
    private BigDecimal variancePercent;
    private BigDecimal varianceAmount;
    private BigDecimal varianceAmountPercent;
    private boolean withinTolerance;
    private String toleranceType;
    private BigDecimal toleranceValue;
    private String severityBand;
    private String description;
}
