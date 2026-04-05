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
public class QuantityTotalsDto {
    private BigDecimal sourceQuantityTotal;
    private BigDecimal targetQuantityTotal;
    private BigDecimal quantityVarianceTotal;
    private long quantityMetricsTransactionCount;
}
