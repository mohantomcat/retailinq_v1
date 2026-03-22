package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScorecardSummaryDto {
    private String label;
    private long totalTransactions;
    private long matchedTransactions;
    private long exceptionCount;
    private long missingCount;
    private long duplicateCount;
    private long activeExceptions;
    private long breachedExceptions;
    private double matchRate;
    private double exceptionRate;
    private double duplicateRate;
    private double slaBreachRate;
    private int healthScore;
    private String healthBand;
}
