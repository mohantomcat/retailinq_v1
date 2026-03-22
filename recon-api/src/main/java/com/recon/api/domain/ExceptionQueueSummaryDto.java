package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionQueueSummaryDto {
    private long openCases;
    private long unassignedCases;
    private long myCases;
    private long myTeamCases;
    private long overdueCases;
    private long highSeverityCases;
    private long highImpactCases;
    private long escalatedCases;
    private long unassignedHighImpactCases;
    private long storeIncidentCount;
    private long storesAtRisk;
    private long ownershipGapCases;
    private long actionDueCases;
}
