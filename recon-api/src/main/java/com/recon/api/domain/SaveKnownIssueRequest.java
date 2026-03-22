package com.recon.api.domain;

import lombok.Data;

@Data
public class SaveKnownIssueRequest {
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
    private Boolean active;
}
