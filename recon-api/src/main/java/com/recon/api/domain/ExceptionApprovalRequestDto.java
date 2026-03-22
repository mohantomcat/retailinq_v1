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
public class ExceptionApprovalRequestDto {
    private UUID id;
    private UUID caseId;
    private UUID policyId;
    private String transactionKey;
    private String reconView;
    private String previousCaseStatus;
    private String requestedCaseStatus;
    private String requestedSeverity;
    private String requestedReasonCode;
    private String requestedRootCauseCategory;
    private String requestedAssigneeUsername;
    private String requestedAssignedRoleName;
    private String requestedNotes;
    private String closureComment;
    private String policyName;
    private String approverRoleName;
    private String requestStatus;
    private String requestedBy;
    private String decisionBy;
    private String decisionNotes;
    private String requestedAt;
    private String decisionAt;
    private String currentCaseStatus;
    private String storeId;
    private String wkstnId;
    private String businessDate;
    private String slaStatus;
    private String dueAt;
}
