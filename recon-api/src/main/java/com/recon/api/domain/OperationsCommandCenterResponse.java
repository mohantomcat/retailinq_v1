package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationsCommandCenterResponse {
    private OperationsCommandCenterSummaryDto summary;
    private List<OperationsCommandCenterActionDto> priorityActions;
    private List<OperationModuleStatusDto> integrationHealth;
    private List<RegionalIncidentOutbreakDto> outbreaks;
    private List<OperationsCommandCenterQueueLaneDto> queueLanes;
    private List<ExceptionSuppressionAuditDto> recentAutomation;
}
