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
public class RecurrenceTrendPointDto {
    private String label;
    private String startDate;
    private String endDate;
    private long repeatCases;
    private long recurringIncidentPatterns;
    private long repeatAfterResolvedCases;
    private BigDecimal repeatValueAtRisk;
}
