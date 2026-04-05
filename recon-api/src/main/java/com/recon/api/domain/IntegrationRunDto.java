package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationRunDto {
    private UUID id;
    private String connectorKey;
    private String flowKey;
    private String sourceSystem;
    private String targetSystem;
    private String endpointMode;
    private boolean sharedAsset;
    private java.util.List<String> affectedReconViews;
    private java.util.List<String> affectedReconLabels;
    private String triggerType;
    private String runStatus;
    private String startedAt;
    private String completedAt;
    private int sourceRecordCount;
    private int publishedRecordCount;
    private int errorCount;
    private String runSummary;
}
