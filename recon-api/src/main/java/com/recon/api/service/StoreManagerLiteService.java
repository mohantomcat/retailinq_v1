package com.recon.api.service;

import com.recon.api.domain.BusinessValueContextDto;
import com.recon.api.domain.ExceptionQueueItemDto;
import com.recon.api.domain.ExceptionQueueResponse;
import com.recon.api.domain.ExceptionStoreIncidentDto;
import com.recon.api.domain.StoreManagerLiteActionItemDto;
import com.recon.api.domain.StoreManagerLiteIncidentDto;
import com.recon.api.domain.StoreManagerLiteResponse;
import com.recon.api.domain.StoreManagerLiteSummaryDto;
import com.recon.api.domain.TenantConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StoreManagerLiteService {

    private final ExceptionQueueService exceptionQueueService;
    private final ExceptionBusinessValueService exceptionBusinessValueService;
    private final ExceptionCollaborationService exceptionCollaborationService;
    private final TenantOperatingModelService tenantOperatingModelService;
    private final TenantService tenantService;

    public StoreManagerLiteService(ExceptionQueueService exceptionQueueService,
                                   ExceptionBusinessValueService exceptionBusinessValueService,
                                   ExceptionCollaborationService exceptionCollaborationService,
                                   TenantOperatingModelService tenantOperatingModelService,
                                   TenantService tenantService) {
        this.exceptionQueueService = exceptionQueueService;
        this.exceptionBusinessValueService = exceptionBusinessValueService;
        this.exceptionCollaborationService = exceptionCollaborationService;
        this.tenantOperatingModelService = tenantOperatingModelService;
        this.tenantService = tenantService;
    }

    @Transactional(readOnly = true)
    public StoreManagerLiteResponse getView(String tenantId,
                                            String username,
                                            Set<String> accessibleStores,
                                            String reconView,
                                            String storeId,
                                            String search) {
        ExceptionQueueResponse queue = exceptionQueueService.getQueue(
                tenantId,
                username,
                accessibleStores,
                reconView,
                "ALL",
                null,
                null,
                null,
                null,
                search
        );
        TenantConfig tenant = tenantService.getTenant(tenantId);
        LocalDate currentBusinessDate = tenantOperatingModelService.currentBusinessDate(tenant);

        Set<String> allowedStores = normalizeStoreSet(accessibleStores);
        String requestedStore = normalize(storeId);

        List<ExceptionQueueItemDto> items = (queue.getItems() == null ? List.<ExceptionQueueItemDto>of() : queue.getItems())
                .stream()
                .filter(item -> matchesStoreScope(item.getStoreId(), allowedStores, requestedStore))
                .toList();

        List<ExceptionStoreIncidentDto> incidents = (queue.getStoreIncidents() == null ? List.<ExceptionStoreIncidentDto>of() : queue.getStoreIncidents())
                .stream()
                .filter(incident -> matchesStoreScope(incident.getStoreId(), allowedStores, requestedStore))
                .toList();

        Map<String, Boolean> activeTodayByIncident = items.stream()
                .collect(Collectors.groupingBy(
                        ExceptionQueueItemDto::getStoreIncidentKey,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                groupedItems -> groupedItems.stream().anyMatch(item -> isActiveToday(item, currentBusinessDate))
                        )));

        BusinessValueContextDto summaryBusinessValue = exceptionBusinessValueService.aggregateIncidentValue(
                incidents.stream()
                        .map(ExceptionStoreIncidentDto::getBusinessValue)
                        .filter(Objects::nonNull)
                        .toList(),
                items.size()
        );
        Map<String, List<com.recon.api.domain.ExceptionExternalTicketDto>> ticketsByIncident = exceptionCollaborationService.getIncidentExternalTickets(
                tenantId,
                incidents.stream().map(ExceptionStoreIncidentDto::getIncidentKey).filter(Objects::nonNull).toList()
        );
        Map<String, List<com.recon.api.domain.ExceptionOutboundCommunicationDto>> communicationsByIncident = exceptionCollaborationService.getIncidentCommunications(
                tenantId,
                incidents.stream().map(ExceptionStoreIncidentDto::getIncidentKey).filter(Objects::nonNull).toList()
        );

        List<StoreManagerLiteIncidentDto> incidentCards = incidents.stream()
                .sorted(Comparator
                        .comparing((ExceptionStoreIncidentDto incident) -> Boolean.TRUE.equals(activeTodayByIncident.get(incident.getIncidentKey())) ? 0 : 1)
                        .thenComparing(ExceptionStoreIncidentDto::getImpactScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ExceptionStoreIncidentDto::getLatestUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(incident -> toIncidentCard(
                        incident,
                        Boolean.TRUE.equals(activeTodayByIncident.get(incident.getIncidentKey())),
                        ticketsByIncident.getOrDefault(incident.getIncidentKey(), List.of()),
                        communicationsByIncident.getOrDefault(incident.getIncidentKey(), List.of())))
                .toList();

        List<StoreManagerLiteActionItemDto> actionItems = incidentCards.stream()
                .filter(this::requiresAction)
                .sorted(Comparator
                        .comparingInt((StoreManagerLiteIncidentDto incident) -> actionUrgencyRank(incident.getOwnershipStatus())).reversed()
                        .thenComparing(StoreManagerLiteIncidentDto::getImpactScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(6)
                .map(this::toActionItem)
                .toList();

        List<String> storeOptions = items.stream()
                .map(ExceptionQueueItemDto::getStoreId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .toList();

        return StoreManagerLiteResponse.builder()
                .asOfBusinessDate(currentBusinessDate.toString())
                .operatingModel(tenantOperatingModelService.toDto(tenant))
                .storeOptions(storeOptions)
                .ticketChannels(exceptionCollaborationService.getActiveChannels(tenantId, reconView, "TICKETING"))
                .communicationChannels(exceptionCollaborationService.getActiveChannels(tenantId, reconView, "COMMUNICATION"))
                .summary(StoreManagerLiteSummaryDto.builder()
                        .storesInView(storeOptions.size())
                        .activeIncidents(incidents.size())
                        .affectedTransactions(incidents.stream()
                                .map(ExceptionStoreIncidentDto::getImpactedTransactions)
                                .filter(Objects::nonNull)
                                .mapToLong(Long::longValue)
                                .sum())
                        .todayAffectedTransactions(items.stream()
                                .filter(item -> isActiveToday(item, currentBusinessDate))
                                .map(ExceptionQueueItemDto::getTransactionKey)
                                .filter(Objects::nonNull)
                                .distinct()
                                .count())
                        .openCases(items.size())
                        .actionRequiredIncidents(incidentCards.stream().filter(this::requiresAction).count())
                        .overdueActionIncidents(incidentCards.stream().filter(incident -> isOverdueAction(incident.getOwnershipStatus())).count())
                        .businessValue(summaryBusinessValue)
                        .build())
                .actionItems(actionItems)
                .incidents(incidentCards)
                .build();
    }

    private StoreManagerLiteIncidentDto toIncidentCard(ExceptionStoreIncidentDto incident,
                                                       boolean activeToday,
                                                       List<com.recon.api.domain.ExceptionExternalTicketDto> externalTickets,
                                                       List<com.recon.api.domain.ExceptionOutboundCommunicationDto> communications) {
        return StoreManagerLiteIncidentDto.builder()
                .incidentKey(incident.getIncidentKey())
                .storeId(incident.getStoreId())
                .reconView(incident.getReconView())
                .incidentTitle(incident.getIncidentTitle())
                .incidentSummary(incident.getIncidentSummary())
                .impactScore(incident.getImpactScore())
                .impactBand(incident.getImpactBand())
                .ownershipStatus(incident.getOwnershipStatus())
                .activeToday(activeToday)
                .openCases(incident.getOpenCaseCount())
                .impactedTransactions(incident.getImpactedTransactions())
                .overdueCases(incident.getOverdueCases())
                .ownerSummary(incident.getOwnerSummary())
                .nextAction(incident.getNextAction())
                .nextActionDueAt(incident.getNextActionDueAt())
                .priorityReason(incident.getPriorityReason())
                .latestUpdatedAt(incident.getLatestUpdatedAt())
                .businessValue(incident.getBusinessValue())
                .matchedKnownIssue(incident.getMatchedKnownIssue())
                .externalTickets(externalTickets)
                .communications(communications)
                .recommendedAction(recommendedAction(incident))
                .build();
    }

    private StoreManagerLiteActionItemDto toActionItem(StoreManagerLiteIncidentDto incident) {
        return StoreManagerLiteActionItemDto.builder()
                .storeId(incident.getStoreId())
                .incidentKey(incident.getIncidentKey())
                .incidentTitle(incident.getIncidentTitle())
                .ownershipStatus(incident.getOwnershipStatus())
                .actionLabel(Objects.requireNonNullElse(incident.getRecommendedAction(), "Review incident"))
                .actionReason(actionReason(incident))
                .ownerSummary(incident.getOwnerSummary())
                .dueAt(incident.getNextActionDueAt())
                .priorityReason(incident.getPriorityReason())
                .impactScore(incident.getImpactScore())
                .impactBand(incident.getImpactBand())
                .businessValue(incident.getBusinessValue())
                .build();
    }

    private boolean matchesStoreScope(String storeId, Set<String> allowedStores, String requestedStore) {
        String normalized = normalize(storeId);
        if (requestedStore != null) {
            return Objects.equals(requestedStore, normalized)
                    && (allowedStores.isEmpty() || allowedStores.contains(normalized));
        }
        return allowedStores.isEmpty() || allowedStores.contains(normalized);
    }

    private boolean isActiveToday(ExceptionQueueItemDto item, LocalDate currentBusinessDate) {
        return Objects.equals(currentBusinessDate.toString(), item.getBusinessDate());
    }

    private boolean requiresAction(StoreManagerLiteIncidentDto incident) {
        return isOverdueAction(incident.getOwnershipStatus())
                || isOwnershipGap(incident.getOwnershipStatus())
                || incident.getNextAction() != null;
    }

    private boolean isOwnershipGap(String ownershipStatus) {
        String normalized = normalize(ownershipStatus);
        return "UNOWNED".equals(normalized)
                || "NO_NEXT_ACTION".equals(normalized)
                || "OWNERSHIP_GAP".equals(normalized);
    }

    private boolean isOverdueAction(String ownershipStatus) {
        return "ACTION_OVERDUE".equals(normalize(ownershipStatus));
    }

    private int actionUrgencyRank(String ownershipStatus) {
        String normalized = normalize(ownershipStatus);
        if ("ACTION_OVERDUE".equals(normalized)) {
            return 4;
        }
        if ("UNOWNED".equals(normalized) || "OWNERSHIP_GAP".equals(normalized)) {
            return 3;
        }
        if ("NO_NEXT_ACTION".equals(normalized)) {
            return 2;
        }
        if ("ACTION_DUE_SOON".equals(normalized)) {
            return 1;
        }
        return 0;
    }

    private String recommendedAction(ExceptionStoreIncidentDto incident) {
        String ownershipStatus = normalize(incident.getOwnershipStatus());
        if ("ACTION_OVERDUE".equals(ownershipStatus)) {
            return "Complete or escalate the overdue next action";
        }
        if ("UNOWNED".equals(ownershipStatus) || "OWNERSHIP_GAP".equals(ownershipStatus)) {
            return "Assign an owner and confirm the store response";
        }
        if ("NO_NEXT_ACTION".equals(ownershipStatus)) {
            return "Define the next action for the store team";
        }
        if (incident.getNextAction() != null && !incident.getNextAction().isBlank()) {
            return incident.getNextAction();
        }
        return "Review impacted transactions and confirm recovery";
    }

    private String actionReason(StoreManagerLiteIncidentDto incident) {
        if (isOverdueAction(incident.getOwnershipStatus())) {
            return "The current action due time has already passed";
        }
        if (isOwnershipGap(incident.getOwnershipStatus())) {
            return "This incident does not yet have complete ownership or next-step guidance";
        }
        if (incident.isActiveToday()) {
            return "This issue is impacting today's store operations";
        }
        return "This incident still requires store follow-up";
    }

    private Set<String> normalizeStoreSet(Set<String> stores) {
        if (stores == null) {
            return Set.of();
        }
        return stores.stream()
                .map(this::normalize)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private String normalize(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
