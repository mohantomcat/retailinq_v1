package com.recon.api.domain;

import lombok.Data;

@Data
public class SaveExceptionClosurePolicyRequest {
    private String policyName;
    private String reconView;
    private String targetStatus;
    private String minSeverity;
    private boolean requireReasonCode;
    private boolean requireRootCauseCategory;
    private boolean requireNotes;
    private boolean requireComment;
    private boolean requireApproval;
    private String approverRoleName;
    private boolean active = true;
    private String description;
}
