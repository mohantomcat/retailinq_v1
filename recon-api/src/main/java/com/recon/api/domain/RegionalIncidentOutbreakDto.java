package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionalIncidentOutbreakDto {
    private String outbreakKey;
    private String clusterKey;
    private String clusterLabel;
    private String reconView;
    private String incidentTitle;
    private String incidentSummary;
    private String outbreakStatus;
    private boolean outbreakDetected;
    private Integer impactScore;
    private String impactBand;
    private Long affectedStores;
    private Long activeStoreIncidents;
    private Long openCases;
    private Long impactedTransactions;
    private Long overdueCases;
    private Long unassignedCases;
    private Long ownershipGapCases;
    private String ownerSummary;
    private String nextAction;
    private String priorityReason;
    private String startedAt;
    private String latestUpdatedAt;
    private BusinessValueContextDto businessValue;
    private List<String> affectedStoreIds;
    private List<RegionalIncidentStoreSignalDto> storeSignals;
}
