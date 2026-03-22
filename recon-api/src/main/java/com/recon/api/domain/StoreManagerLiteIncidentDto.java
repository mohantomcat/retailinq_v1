package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreManagerLiteIncidentDto {
    private String incidentKey;
    private String storeId;
    private String reconView;
    private String incidentTitle;
    private String incidentSummary;
    private Integer impactScore;
    private String impactBand;
    private String ownershipStatus;
    private boolean activeToday;
    private Long openCases;
    private Long impactedTransactions;
    private Long overdueCases;
    private String ownerSummary;
    private String nextAction;
    private String nextActionDueAt;
    private String priorityReason;
    private String latestUpdatedAt;
    private BusinessValueContextDto businessValue;
    private KnownIssueMatchDto matchedKnownIssue;
    private java.util.List<ExceptionExternalTicketDto> externalTickets;
    private java.util.List<ExceptionOutboundCommunicationDto> communications;
    private String recommendedAction;
}
