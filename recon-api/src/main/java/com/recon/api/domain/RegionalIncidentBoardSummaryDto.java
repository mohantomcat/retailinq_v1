package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionalIncidentBoardSummaryDto {
    private long regionalGroups;
    private long detectedOutbreaks;
    private long spreadingOutbreaks;
    private long impactedClusters;
    private long impactedStores;
    private long openCases;
    private BusinessValueContextDto businessValue;
}
