package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationModuleStatusDto {
    private String moduleId;
    private String moduleLabel;
    private String reconView;
    private String reconLabel;
    private String category;
    private String systemName;
    private String endpointMode;
    private boolean sharedAsset;
    private List<String> affectedReconViews;
    private List<String> affectedReconLabels;
    private boolean reachable;
    private List<String> availableActions;
    private List<String> advancedActions;
    private Map<String, Object> status;
    private String healthStatus;
    private Integer healthScore;
    private String freshnessStatus;
    private Long freshnessLagMinutes;
    private Long backlogCount;
    private Long activeCaseCount;
    private Long breachedCaseCount;
    private String lastSuccessfulSyncAt;
    private List<String> statusHighlights;
    private String recommendedAction;
    private String resetPayloadMode;
    private boolean supportsRegisterFilter;
}
