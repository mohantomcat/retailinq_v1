package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringIncidentPatternDto {
    private String recurrenceKey;
    private String storeId;
    private String reconView;
    private String incidentTitle;
    private String incidentSummary;
    private long totalCases;
    private long activeCases;
    private long repeatCases;
    private long repeatAfterResolvedCases;
    private long repeatWithin7DaysCases;
    private long repeatWithin14DaysCases;
    private long repeatWithin30DaysCases;
    private String ownerTeam;
    private String latestCaseStatus;
    private String latestSeenAt;
    private String firstSeenAt;
    private String priorityReason;
    private String preventionAction;
    private BusinessValueContextDto businessValue;
    private KnownIssueMatchDto matchedKnownIssue;
}
