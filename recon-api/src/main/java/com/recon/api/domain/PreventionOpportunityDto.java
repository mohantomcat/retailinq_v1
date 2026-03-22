package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreventionOpportunityDto {
    private String recurrenceKey;
    private String title;
    private String storeId;
    private String reconView;
    private String ownerTeam;
    private long repeatCases;
    private long repeatAfterResolvedCases;
    private String opportunityReason;
    private String recommendedAction;
    private String lastSeenAt;
    private BusinessValueContextDto businessValue;
    private KnownIssueMatchDto matchedKnownIssue;
}
