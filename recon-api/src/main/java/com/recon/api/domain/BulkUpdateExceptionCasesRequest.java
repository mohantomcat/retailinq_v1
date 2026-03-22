package com.recon.api.domain;

import lombok.Data;

import java.util.List;

@Data
public class BulkUpdateExceptionCasesRequest {
    private List<BulkExceptionCaseRefRequest> items;
    private String caseStatus;
    private String assigneeUsername;
    private String assignedRoleName;
    private String nextAction;
    private String nextActionDueAt;
    private String handoffNote;
    private String reasonCode;
    private String commentText;
    private String reopenReason;
    private Boolean captureAuditSnapshot;
}
