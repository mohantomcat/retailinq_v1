package com.recon.flink.domain;

import java.io.Serializable;
import java.math.BigDecimal;

public record MatchToleranceProfile(
        String profileName,
        BigDecimal quantityAbsoluteTolerance,
        BigDecimal quantityPercentTolerance,
        BigDecimal amountAbsoluteTolerance,
        BigDecimal amountPercentTolerance
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
