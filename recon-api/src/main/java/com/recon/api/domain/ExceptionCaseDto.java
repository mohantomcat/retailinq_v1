package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionCaseDto {
    private UUID id;
    private String tenantId;
    private String transactionKey;
    private String reconView;
    private String reconStatus;
    private String caseStatus;
    private String reasonCode;
    private String rootCauseCategory;
    private String severity;
    private String assigneeUsername;
    private String assignedRoleName;
    private String nextAction;
    private String nextActionDueAt;
    private String handoffNote;
    private String lastHandoffBy;
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
    private String lastEscalatedBy;
    private String lastEscalatedAt;
    private String escalationPolicyName;
    private String escalationReason;
    private String reopenReason;
    private Integer reopenCount;
    private String lastReopenedBy;
    private String lastReopenedAt;
    private Integer slaTargetMinutes;
    private String dueAt;
    private String breachedAt;
    private String slaStatus;
    private String approvalState;
    private String workflowMessage;
    private BusinessValueContextDto businessValue;
    private KnownIssueMatchDto matchedKnownIssue;
    private List<ExceptionIntegrationChannelDto> ticketChannels;
    private List<ExceptionIntegrationChannelDto> communicationChannels;
    private List<ExceptionExternalTicketDto> externalTickets;
    private List<ExceptionOutboundCommunicationDto> communications;
    private ExceptionApprovalRequestDto pendingApprovalRequest;
    private ExceptionPlaybookDto recommendedPlaybook;
    private String createdBy;
    private String updatedBy;
    private String createdAt;
    private String updatedAt;
    private List<ExceptionCommentDto> comments;
    private List<ExceptionCaseTimelineEventDto> timeline;
    private List<ExceptionCaseAuditSnapshotDto> auditSnapshots;
}
