package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditRetentionPolicyDto {
    private String policyName;
    private Integer minimumRetentionDays;
    private Integer archiveAfterDays;
    private boolean legalHold;
    private String defaultExportFormat;
    private String notes;
    private String createdBy;
    private String updatedBy;
    private String createdAt;
    private String updatedAt;
}
