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
public class ExceptionQueueItemDto {
    private UUID id;
    private String transactionKey;
    private String reconView;
    private String reconStatus;
    private String caseStatus;
    private String reasonCode;
    private String severity;
    private String assigneeUsername;
    private String assignedRoleName;
    private String nextAction;
    private String nextActionDueAt;
    private String lastHandoffAt;
    private String ownershipStatus;
    private String storeId;
    private String wkstnId;
    private String businessDate;
    private boolean autoAssigned;
    private String routingRuleName;
    private String playbookName;
    private String notes;
    private String escalationState;
    private Integer escalationCount;
    private String lastEscalatedAt;
    private String escalationPolicyName;
    private String slaStatus;
    private String approvalState;
    private Integer slaTargetMinutes;
    private String dueAt;
    private String createdAt;
    private String updatedAt;
    private String storeIncidentKey;
    private String storeIncidentTitle;
    private BusinessValueContextDto businessValue;
    private KnownIssueMatchDto matchedKnownIssue;
    private Integer impactScore;
    private String impactBand;
    private String priorityReason;
    private Long storeOpenCaseCount;
    private Long repeatIssueCount;
    private Long caseAgeHours;
}
