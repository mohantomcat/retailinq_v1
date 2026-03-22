package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurrenceAnalyticsSummaryDto {
    private long totalCases;
    private long repeatCases;
    private double repeatCaseRate;
    private long recurringIncidentPatterns;
    private long repeatAfterResolvedCases;
    private long repeatWithin7DaysCases;
    private long repeatWithin14DaysCases;
    private long repeatWithin30DaysCases;
    private long preventionOpportunityCount;
    private BusinessValueContextDto repeatBusinessValue;
}
