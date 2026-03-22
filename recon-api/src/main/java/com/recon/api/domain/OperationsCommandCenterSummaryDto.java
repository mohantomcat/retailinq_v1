package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationsCommandCenterSummaryDto {
    private long openCases;
    private long overdueCases;
    private long activeIncidents;
    private long storesAtRisk;
    private long detectedOutbreaks;
    private long spreadingOutbreaks;
    private long unhealthyIntegrations;
    private long staleIntegrations;
    private long autoResolvedLast7Days;
    private long suppressedLast7Days;
    private long failedDeliveries;
    private BusinessValueContextDto businessValue;
}
