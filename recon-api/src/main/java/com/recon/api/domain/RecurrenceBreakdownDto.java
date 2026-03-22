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
public class RecurrenceBreakdownDto {
    private String key;
    private String label;
    private long recurringIncidentCount;
    private long repeatCases;
    private long repeatAfterResolvedCases;
    private long repeatWithin7DaysCases;
    private long repeatWithin14DaysCases;
    private long repeatWithin30DaysCases;
    private double repeatShare;
    private BigDecimal valueAtRisk;
}
