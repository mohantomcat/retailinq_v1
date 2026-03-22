package com.recon.flink.domain;

import java.io.Serializable;
import java.math.BigDecimal;

public record MatchEvaluation(
        int matchScore,
        String matchBand,
        String matchRule,
        String matchSummary,
        boolean toleranceApplied,
        String toleranceProfile,
        int matchedLineCount,
        int discrepantLineCount,
        int toleratedDiscrepancyCount,
        int materialDiscrepancyCount,
        BigDecimal quantityVariancePercent,
        BigDecimal amountVariancePercent
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
