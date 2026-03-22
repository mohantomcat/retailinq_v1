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
public class BusinessValueContextDto {
    private String currencyCode;
    private BigDecimal valueAtRisk;
    private BigDecimal amountVariance;
    private Integer lineItemCount;
    private Integer affectedItemCount;
    private BigDecimal totalQuantity;
    private BigDecimal quantityImpact;
    private Integer businessValueScore;
    private String businessValueBand;
    private String customerImpact;
    private String summary;
}
