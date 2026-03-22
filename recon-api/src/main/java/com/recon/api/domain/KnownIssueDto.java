package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnownIssueDto {
    private UUID id;
    private String issueKey;
    private String title;
    private String issueSummary;
    private String reconView;
    private String reconStatus;
    private String reasonCode;
    private String rootCauseCategory;
    private String storeId;
    private String matchKeywords;
    private String probableCause;
    private String recommendedAction;
    private String escalationGuidance;
    private String resolverNotes;
    private Integer priorityWeight;
    private boolean active;
    private Long helpfulCount;
    private Long notHelpfulCount;
    private String createdBy;
    private String updatedBy;
    private String createdAt;
    private String updatedAt;
}
