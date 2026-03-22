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
public class ExceptionClosurePolicyDto {
    private UUID id;
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
    private boolean active;
    private String description;
    private String createdBy;
    private String updatedBy;
    private String createdAt;
    private String updatedAt;
}
