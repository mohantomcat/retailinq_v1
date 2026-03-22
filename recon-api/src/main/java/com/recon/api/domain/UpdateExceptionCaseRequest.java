package com.recon.api.domain;

import lombok.Data;

@Data
public class UpdateExceptionCaseRequest {
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
    private String storeId;
    private String wkstnId;
    private String businessDate;
    private String notes;
    private String closureComment;
    private String reopenReason;
    private Boolean captureAuditSnapshot;
}
