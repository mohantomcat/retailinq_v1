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
public class KnownIssueMatchDto {
    private UUID id;
    private String issueKey;
    private String title;
    private String issueSummary;
    private String probableCause;
    private String recommendedAction;
    private String escalationGuidance;
    private String resolverNotes;
    private String confidence;
    private String matchReason;
    private Integer matchScore;
    private Long helpfulCount;
    private Long notHelpfulCount;
}
