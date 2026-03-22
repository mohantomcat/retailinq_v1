package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveAuditRetentionPolicyRequest {
    private String policyName;
    private Integer minimumRetentionDays;
    private Integer archiveAfterDays;
    private Boolean legalHold;
    private String defaultExportFormat;
    private String notes;
}
