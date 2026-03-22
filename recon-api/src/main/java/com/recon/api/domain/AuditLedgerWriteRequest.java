package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLedgerWriteRequest {
    private String tenantId;
    private String sourceType;
    private String moduleKey;
    private String entityType;
    private String entityKey;
    private String actionType;
    private String title;
    private String summary;
    private String actor;
    private String reason;
    private String status;
    private String referenceKey;
    private String controlFamily;
    private List<String> evidenceTags;
    private Object beforeState;
    private Object afterState;
    private Object metadata;
    private LocalDateTime eventAt;
}
