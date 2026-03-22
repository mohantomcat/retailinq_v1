package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoxAuditSummaryDto {
    private String periodStart;
    private String periodEnd;
    private long totalAuditableActions;
    private long configurationChanges;
    private long securityAdminChanges;
    private long exceptionControlActions;
    private long operationalInterventions;
    private long approvalDecisions;
    private long exportsGenerated;
    private String lastArchiveAt;
    private boolean legalHold;
}
