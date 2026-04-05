package com.recon.api.service;

import com.recon.api.domain.BusinessValueContextDto;
import com.recon.api.domain.ExceptionQueueItemDto;
import com.recon.api.domain.ExceptionQueueResponse;
import com.recon.api.domain.ExceptionStoreIncidentDto;
import com.recon.api.domain.ExceptionSuppressionAuditDto;
import com.recon.api.domain.ExceptionSuppressionSummaryDto;
import com.recon.api.domain.ExceptionTicketingCenterResponse;
import com.recon.api.domain.OperationModuleStatusDto;
import com.recon.api.domain.OperationsCommandCenterActionDto;
import com.recon.api.domain.OperationsCommandCenterQueueLaneDto;
import com.recon.api.domain.OperationsCommandCenterResponse;
import com.recon.api.domain.OperationsCommandCenterSummaryDto;
import com.recon.api.domain.OperationsResponse;
import com.recon.api.domain.ReconModuleDto;
import com.recon.api.domain.RegionalIncidentBoardResponse;
import com.recon.api.domain.RegionalIncidentOutbreakDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OperationsCommandCenterService {

    private static final int HIGH_IMPACT_THRESHOLD = 70;

    private final ExceptionQueueService exceptionQueueService;
    private final RegionalIncidentBoardService regionalIncidentBoardService;
    private final OperationsService operationsService;
    private final ExceptionNoiseSuppressionService exceptionNoiseSuppressionService;
    private final ExceptionCollaborationService exceptionCollaborationService;
    private final ExceptionBusinessValueService exceptionBusinessValueService;
    private final ReconModuleService reconModuleService;

    @Transactional(readOnly = true)
    public OperationsCommandCenterResponse getCenter(String tenantId,
                                                     String username,
                                                     java.util.Collection<String> accessibleStoreIds,
                                                     List<String> allowedReconViews,
                                                     String reconView) {
        List<String> scopedViews = resolveViews(allowedReconViews, reconView);
        List<ExceptionQueueResponse> queues = scopedViews.stream()
                .map(view -> exceptionQueueService.getQueue(
                        tenantId,
                        username,
                        accessibleStoreIds,
                        scopedViews,
                        view,
                        "ALL",
                        null,
                        null,
                        null,
                        null,
                        null))
                .toList();

        List<ExceptionQueueItemDto> items = queues.stream()
                .flatMap(queue -> (queue.getItems() == null ? List.<ExceptionQueueItemDto>of() : queue.getItems()).stream())
                .toList();
        List<ExceptionStoreIncidentDto> incidents = queues.stream()
                .flatMap(queue -> (queue.getStoreIncidents() == null ? List.<ExceptionStoreIncidentDto>of() : queue.getStoreIncidents()).stream())
                .toList();

        RegionalIncidentBoardResponse regionalBoard = regionalIncidentBoardService.getBoard(
                tenantId,
                username,
                accessibleStoreIds,
                scopedViews,
                reconView,
                null,
                null
        );
        OperationsResponse operations = operationsService.getOperations(tenantId, scopedViews);
        List<OperationModuleStatusDto> integrationHealth = (operations.getModules() == null ? List.<OperationModuleStatusDto>of() : operations.getModules()).stream()
                .filter(module -> scopedViews.isEmpty() || scopedViews.contains(normalize(module.getReconView())))
                .sorted(Comparator
                        .comparingInt((OperationModuleStatusDto module) -> healthRank(module.getHealthStatus())).reversed()
                        .thenComparing(OperationModuleStatusDto::getHealthScore, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        ExceptionSuppressionSummaryDto suppressionSummary = exceptionNoiseSuppressionService.getSummary(tenantId, reconView, scopedViews);
        List<ExceptionSuppressionAuditDto> recentAutomation = exceptionNoiseSuppressionService.getRecentActivity(tenantId, reconView, scopedViews).stream()
                .limit(8)
                .toList();
        ExceptionTicketingCenterResponse ticketingCenter = exceptionCollaborationService.getCenter(tenantId, reconView, scopedViews);

        BusinessValueContextDto summaryBusinessValue = exceptionBusinessValueService.aggregateIncidentValue(
                incidents.stream()
                        .map(ExceptionStoreIncidentDto::getBusinessValue)
                        .filter(Objects::nonNull)
                        .toList(),
                items.size()
        );

        List<OperationsCommandCenterQueueLaneDto> queueLanes = buildQueueLanes(scopedViews, items);
        List<RegionalIncidentOutbreakDto> outbreaks = (regionalBoard.getOutbreaks() == null ? List.<RegionalIncidentOutbreakDto>of() : regionalBoard.getOutbreaks()).stream()
                .limit(6)
                .toList();

        return OperationsCommandCenterResponse.builder()
                .summary(OperationsCommandCenterSummaryDto.builder()
                        .openCases(items.size())
                        .overdueCases(items.stream().filter(item -> "BREACHED".equalsIgnoreCase(item.getSlaStatus())).count())
                        .activeIncidents(incidents.size())
                        .storesAtRisk(incidents.stream().map(ExceptionStoreIncidentDto::getStoreId).filter(Objects::nonNull).distinct().count())
                        .detectedOutbreaks((regionalBoard.getSummary() != null ? regionalBoard.getSummary().getDetectedOutbreaks() : 0))
                        .spreadingOutbreaks((regionalBoard.getSummary() != null ? regionalBoard.getSummary().getSpreadingOutbreaks() : 0))
                        .unhealthyIntegrations(integrationHealth.stream().filter(module -> !"HEALTHY".equalsIgnoreCase(module.getHealthStatus())).count())
                        .staleIntegrations(integrationHealth.stream().filter(module -> "STALE".equalsIgnoreCase(module.getFreshnessStatus())).count())
                        .autoResolvedLast7Days(suppressionSummary.getAutoResolvedLast7Days())
                        .suppressedLast7Days(suppressionSummary.getSuppressedLast7Days())
                        .failedDeliveries(ticketingCenter.getSummary() != null ? ticketingCenter.getSummary().getFailedDeliveries() : 0)
                        .businessValue(summaryBusinessValue)
                        .build())
                .priorityActions(buildPriorityActions(integrationHealth, outbreaks, queueLanes, ticketingCenter, suppressionSummary))
                .integrationHealth(integrationHealth)
                .outbreaks(outbreaks)
                .queueLanes(queueLanes)
                .recentAutomation(recentAutomation)
                .build();
    }

    private List<OperationsCommandCenterQueueLaneDto> buildQueueLanes(List<String> scopedViews,
                                                                      List<ExceptionQueueItemDto> items) {
        List<String> views = scopedViews.isEmpty()
                ? items.stream().map(ExceptionQueueItemDto::getReconView).filter(Objects::nonNull).distinct().toList()
                : scopedViews;
        return views.stream()
                .map(view -> {
                    List<ExceptionQueueItemDto> viewItems = items.stream()
                            .filter(item -> Objects.equals(normalize(item.getReconView()), normalize(view)))
                            .toList();
                    return OperationsCommandCenterQueueLaneDto.builder()
                            .reconView(view)
                            .label(moduleLabel(view))
                            .openCases(viewItems.size())
                            .overdueCases(viewItems.stream().filter(item -> "BREACHED".equalsIgnoreCase(item.getSlaStatus())).count())
                            .highImpactCases(viewItems.stream()
                                    .filter(item -> item.getImpactScore() != null && item.getImpactScore() >= HIGH_IMPACT_THRESHOLD)
                                    .count())
                            .ownershipGapCases(viewItems.stream()
                                    .filter(item -> isOwnershipGap(item.getOwnershipStatus()))
                                    .count())
                            .actionDueCases(viewItems.stream()
                                    .filter(item -> "ACTION_DUE_SOON".equalsIgnoreCase(item.getOwnershipStatus())
                                            || "ACTION_OVERDUE".equalsIgnoreCase(item.getOwnershipStatus()))
                                    .count())
                            .build();
                })
                .sorted(Comparator
                        .comparingLong(OperationsCommandCenterQueueLaneDto::getHighImpactCases).reversed()
                        .thenComparingLong(OperationsCommandCenterQueueLaneDto::getOverdueCases).reversed())
                .toList();
    }

    private List<OperationsCommandCenterActionDto> buildPriorityActions(List<OperationModuleStatusDto> integrationHealth,
                                                                        List<RegionalIncidentOutbreakDto> outbreaks,
                                                                        List<OperationsCommandCenterQueueLaneDto> queueLanes,
                                                                        ExceptionTicketingCenterResponse ticketingCenter,
                                                                        ExceptionSuppressionSummaryDto suppressionSummary) {
        List<OperationsCommandCenterActionDto> actions = new ArrayList<>();

        integrationHealth.stream()
                .filter(module -> !"HEALTHY".equalsIgnoreCase(module.getHealthStatus()))
                .limit(3)
                .forEach(module -> actions.add(OperationsCommandCenterActionDto.builder()
                        .title(module.getModuleLabel() + " needs attention")
                        .detail(defaultText(module.getRecommendedAction(), "Review connector backlog and freshness signals"))
                        .severity(defaultText(module.getHealthStatus(), "WARNING"))
                        .ownerLane("Integration Operations")
                        .targetTab("operations")
                        .build()));

        outbreaks.stream()
                .filter(outbreak -> "SPREADING".equalsIgnoreCase(outbreak.getOutbreakStatus())
                        || "EMERGING".equalsIgnoreCase(outbreak.getOutbreakStatus()))
                .limit(2)
                .forEach(outbreak -> actions.add(OperationsCommandCenterActionDto.builder()
                        .title(outbreak.getIncidentTitle() + " is spreading")
                        .detail(defaultText(outbreak.getPriorityReason(), "Coordinate regional triage and ownership"))
                        .severity(defaultText(outbreak.getOutbreakStatus(), "HIGH"))
                        .ownerLane("Regional Operations")
                        .targetTab("regional-incident-board")
                        .build()));

        queueLanes.stream()
                .filter(lane -> lane.getOverdueCases() > 0 || lane.getOwnershipGapCases() > 0)
                .limit(2)
                .forEach(lane -> actions.add(OperationsCommandCenterActionDto.builder()
                        .title(lane.getLabel() + " queue pressure")
                        .detail(lane.getOverdueCases() + " overdue cases and " + lane.getOwnershipGapCases() + " ownership gaps")
                        .severity(lane.getOverdueCases() > 0 ? "HIGH" : "MEDIUM")
                        .ownerLane("Exception Triage")
                        .targetTab("exception-queues")
                        .build()));

        if (ticketingCenter.getSummary() != null && ticketingCenter.getSummary().getFailedDeliveries() > 0) {
            actions.add(OperationsCommandCenterActionDto.builder()
                    .title("Ticketing or communication delivery failures detected")
                    .detail(ticketingCenter.getSummary().getFailedDeliveries() + " recent external updates failed delivery")
                    .severity("HIGH")
                    .ownerLane("Support Coordination")
                    .targetTab("ticketing-comms")
                    .build());
        }

        if ((suppressionSummary.getAutoResolvedLast7Days() + suppressionSummary.getSuppressedLast7Days()) == 0) {
            actions.add(OperationsCommandCenterActionDto.builder()
                    .title("Low-risk noise is still fully manual")
                    .detail("Add suppression coverage for repeat low-value issues to reduce analyst workload")
                    .severity("MEDIUM")
                    .ownerLane("Automation")
                    .targetTab("noise-suppression")
                    .build());
        }

        return actions.stream().limit(8).toList();
    }

    private boolean isOwnershipGap(String ownershipStatus) {
        String normalized = normalize(ownershipStatus);
        return "UNOWNED".equals(normalized)
                || "NO_NEXT_ACTION".equals(normalized)
                || "OWNERSHIP_GAP".equals(normalized);
    }

    private int healthRank(String healthStatus) {
        return switch (normalize(healthStatus)) {
            case "CRITICAL" -> 3;
            case "WARNING" -> 2;
            case "HEALTHY" -> 1;
            default -> 0;
        };
    }

    private List<String> resolveViews(List<String> allowedReconViews, String requestedReconView) {
        List<String> accessibleViews = allowedReconViews == null || allowedReconViews.isEmpty()
                ? reconModuleService.getAllActiveModules().stream()
                .map(ReconModuleDto::getReconView)
                .map(this::normalize)
                .filter(Objects::nonNull)
                .distinct()
                .toList()
                : allowedReconViews.stream()
                .map(this::normalize)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (requestedReconView != null && !requestedReconView.isBlank()) {
            String requestedView = normalize(requestedReconView);
            return accessibleViews.contains(requestedView) ? List.of(requestedView) : List.of();
        }
        return accessibleViews;
    }

    private String moduleLabel(String reconView) {
        return reconModuleService.findByReconView(reconView)
                .map(ReconModuleDto::getLabel)
                .orElse(defaultText(reconView, "Reconciliation"));
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String normalize(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
