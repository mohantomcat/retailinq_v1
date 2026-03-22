package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLedgerRecordDto {
    private String sourceType;
    private String moduleKey;
    private String entityType;
    private String entityKey;
    private String actionType;
    private String actor;
    private String title;
    private String summary;
    private String reason;
    private String status;
    private String referenceKey;
    private String controlFamily;
    private String evidenceTags;
    private String beforeState;
    private String afterState;
    private String metadataJson;
    private String eventHash;
    private boolean archived;
    private LocalDateTime eventTimestamp;
}
