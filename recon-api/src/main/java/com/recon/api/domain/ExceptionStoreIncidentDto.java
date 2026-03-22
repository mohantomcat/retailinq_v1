package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionStoreIncidentDto {
    private String incidentKey;
    private String storeId;
    private String reconView;
    private String incidentTitle;
    private String incidentSummary;
    private String severity;
    private Integer impactScore;
    private String impactBand;
    private Long openCaseCount;
    private Long impactedTransactions;
    private Long affectedRegisters;
    private Long overdueCases;
    private Long unassignedCases;
    private String ownerSummary;
    private String nextAction;
    private String nextActionDueAt;
    private String lastHandoffAt;
    private String lastHandoffBy;
    private String ownershipStatus;
    private String priorityReason;
    private String startedAt;
    private String latestUpdatedAt;
    private BusinessValueContextDto businessValue;
    private KnownIssueMatchDto matchedKnownIssue;
}
