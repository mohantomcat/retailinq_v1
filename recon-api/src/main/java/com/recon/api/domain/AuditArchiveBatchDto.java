package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditArchiveBatchDto {
    private java.util.UUID id;
    private String policyName;
    private String archiveReason;
    private String exportFormat;
    private Integer entryCount;
    private String fromEventAt;
    private String toEventAt;
    private String createdBy;
    private String createdAt;
}
