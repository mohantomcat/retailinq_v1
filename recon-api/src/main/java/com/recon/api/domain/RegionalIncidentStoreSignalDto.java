package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionalIncidentStoreSignalDto {
    private String storeId;
    private String incidentKey;
    private String incidentTitle;
    private Integer impactScore;
    private String impactBand;
    private Long openCaseCount;
    private Long impactedTransactions;
    private String ownerSummary;
    private String nextAction;
    private String nextActionDueAt;
    private String latestUpdatedAt;
    private String ownershipStatus;
    private String priorityReason;
    private BusinessValueContextDto businessValue;
}
