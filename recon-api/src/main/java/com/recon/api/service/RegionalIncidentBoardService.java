package com.recon.api.service;

import com.recon.api.domain.BusinessValueContextDto;
import com.recon.api.domain.ExceptionQueueResponse;
import com.recon.api.domain.ExceptionStoreIncidentDto;
import com.recon.api.domain.RegionalIncidentBoardResponse;
import com.recon.api.domain.RegionalIncidentBoardSummaryDto;
import com.recon.api.domain.RegionalIncidentOutbreakDto;
import com.recon.api.domain.RegionalIncidentStoreSignalDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegionalIncidentBoardService {

    private final ExceptionQueueService exceptionQueueService;
    private final ExceptionBusinessValueService exceptionBusinessValueService;

    @Transactional(readOnly = true)
    public RegionalIncidentBoardResponse getBoard(String tenantId,
                                                  String username,
                                                  java.util.Collection<String> accessibleStoreIds,
                                                  List<String> allowedReconViews,
                                                  String reconView,
                                                  String outbreakStatus,
                                                  String search) {
        ExceptionQueueResponse queue = exceptionQueueService.getQueue(
                tenantId,
                username,
                accessibleStoreIds,
                allowedReconViews,
                null,
                "ALL",
                null,
                null,
                null,
                null,
                null
        );

        List<ExceptionStoreIncidentDto> scopedIncidents = (queue.getStoreIncidents() == null ? List.<ExceptionStoreIncidentDto>of() : queue.getStoreIncidents())
                .stream()
                .filter(incident -> matchesReconScope(incident, allowedReconViews, reconView))
                .toList();

        Map<String, List<ExceptionStoreIncidentDto>> grouped = scopedIncidents.stream()
                .collect(Collectors.groupingBy(this::outbreakKey, Collectors.toList()));

        List<RegionalIncidentOutbreakDto> outbreaks = grouped.values().stream()
                .map(this::toOutbreak)
                .filter(outbreak -> matchesOutbreakStatus(outbreak, outbreakStatus))
                .filter(outbreak -> matchesSearch(outbreak, search))
                .sorted(Comparator
                        .comparingInt((RegionalIncidentOutbreakDto outbreak) -> outbreakSeverityRank(outbreak.getOutbreakStatus())).reversed()
                        .thenComparing(RegionalIncidentOutbreakDto::getImpactScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RegionalIncidentOutbreakDto::getAffectedStores, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RegionalIncidentOutbreakDto::getImpactedTransactions, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        BusinessValueContextDto summaryBusinessValue = exceptionBusinessValueService.aggregateIncidentValue(
                outbreaks.stream()
                        .map(RegionalIncidentOutbreakDto::getBusinessValue)
                        .filter(Objects::nonNull)
                        .toList(),
                outbreaks.stream()
                        .map(RegionalIncidentOutbreakDto::getOpenCases)
                        .filter(Objects::nonNull)
                        .mapToLong(Long::longValue)
                        .sum()
        );

        return RegionalIncidentBoardResponse.builder()
                .summary(RegionalIncidentBoardSummaryDto.builder()
                        .regionalGroups(outbreaks.size())
                        .detectedOutbreaks(outbreaks.stream().filter(RegionalIncidentOutbreakDto::isOutbreakDetected).count())
                        .spreadingOutbreaks(outbreaks.stream()
                                .filter(outbreak -> "SPREADING".equalsIgnoreCase(outbreak.getOutbreakStatus()))
                                .count())
                        .impactedClusters(outbreaks.stream()
                                .map(RegionalIncidentOutbreakDto::getClusterKey)
                                .filter(Objects::nonNull)
                                .distinct()
                                .count())
                        .impactedStores(outbreaks.stream()
                                .flatMap(outbreak -> (outbreak.getAffectedStoreIds() == null ? List.<String>of() : outbreak.getAffectedStoreIds()).stream())
                                .filter(Objects::nonNull)
                                .distinct()
                                .count())
                        .openCases(outbreaks.stream()
                                .map(RegionalIncidentOutbreakDto::getOpenCases)
                                .filter(Objects::nonNull)
                                .mapToLong(Long::longValue)
                                .sum())
                        .businessValue(summaryBusinessValue)
                        .build())
                .outbreaks(outbreaks)
                .build();
    }

    private RegionalIncidentOutbreakDto toOutbreak(List<ExceptionStoreIncidentDto> incidents) {
        List<ExceptionStoreIncidentDto> ordered = incidents.stream()
                .sorted(Comparator
                        .comparing(ExceptionStoreIncidentDto::getImpactScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ExceptionStoreIncidentDto::getImpactedTransactions, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        ExceptionStoreIncidentDto topIncident = ordered.get(0);
        ClusterScope cluster = resolveCluster(topIncident.getStoreId());
        long affectedStores = ordered.stream()
                .map(ExceptionStoreIncidentDto::getStoreId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long openCases = ordered.stream()
                .map(ExceptionStoreIncidentDto::getOpenCaseCount)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        long impactedTransactions = ordered.stream()
                .map(ExceptionStoreIncidentDto::getImpactedTransactions)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        long overdueCases = ordered.stream()
                .map(ExceptionStoreIncidentDto::getOverdueCases)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        long unassignedCases = ordered.stream()
                .map(ExceptionStoreIncidentDto::getUnassignedCases)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        long ownershipGapCases = ordered.stream()
                .filter(incident -> isOwnershipGap(incident.getOwnershipStatus()))
                .count();
        BusinessValueContextDto businessValue = exceptionBusinessValueService.aggregateIncidentValue(
                ordered.stream()
                        .map(ExceptionStoreIncidentDto::getBusinessValue)
                        .filter(Objects::nonNull)
                        .toList(),
                openCases
        );

        boolean outbreakDetected = affectedStores >= 2;
        String outbreakStatus = resolveOutbreakStatus(affectedStores, overdueCases, ownershipGapCases);
        int impactScore = Math.min(
                100,
                (topIncident.getImpactScore() == null ? 0 : topIncident.getImpactScore())
                        + (int) Math.min(24L, Math.max(0L, affectedStores - 1L) * 8L)
                        + (int) Math.min(8L, overdueCases * 2L)
                        + (int) Math.min(6L, ownershipGapCases * 2L)
                        + exceptionBusinessValueService.impactBoost(businessValue)
        );

        List<String> affectedStoreIds = ordered.stream()
                .map(ExceptionStoreIncidentDto::getStoreId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .toList();

        return RegionalIncidentOutbreakDto.builder()
                .outbreakKey(outbreakKey(topIncident))
                .clusterKey(cluster.key())
                .clusterLabel(cluster.label())
                .reconView(topIncident.getReconView())
                .incidentTitle(topIncident.getIncidentTitle())
                .incidentSummary(cluster.label() + " | " + affectedStores + " stores | " + impactedTransactions + " transactions impacted")
                .outbreakStatus(outbreakStatus)
                .outbreakDetected(outbreakDetected)
                .impactScore(impactScore)
                .impactBand(impactBand(impactScore))
                .affectedStores(affectedStores)
                .activeStoreIncidents((long) ordered.size())
                .openCases(openCases)
                .impactedTransactions(impactedTransactions)
                .overdueCases(overdueCases)
                .unassignedCases(unassignedCases)
                .ownershipGapCases(ownershipGapCases)
                .ownerSummary(summarizeOwner(ordered))
                .nextAction(summarizeNextAction(ordered))
                .priorityReason(buildPriorityReason(topIncident, affectedStores, ownershipGapCases, outbreakStatus))
                .startedAt(topIncident.getStartedAt())
                .latestUpdatedAt(topIncident.getLatestUpdatedAt())
                .businessValue(businessValue)
                .affectedStoreIds(affectedStoreIds)
                .storeSignals(ordered.stream().map(this::toStoreSignal).toList())
                .build();
    }

    private RegionalIncidentStoreSignalDto toStoreSignal(ExceptionStoreIncidentDto incident) {
        return RegionalIncidentStoreSignalDto.builder()
                .storeId(incident.getStoreId())
                .incidentKey(incident.getIncidentKey())
                .incidentTitle(incident.getIncidentTitle())
                .impactScore(incident.getImpactScore())
                .impactBand(incident.getImpactBand())
                .openCaseCount(incident.getOpenCaseCount())
                .impactedTransactions(incident.getImpactedTransactions())
                .ownerSummary(incident.getOwnerSummary())
                .nextAction(incident.getNextAction())
                .nextActionDueAt(incident.getNextActionDueAt())
                .latestUpdatedAt(incident.getLatestUpdatedAt())
                .ownershipStatus(incident.getOwnershipStatus())
                .priorityReason(incident.getPriorityReason())
                .businessValue(incident.getBusinessValue())
                .build();
    }

    private boolean matchesReconScope(ExceptionStoreIncidentDto incident, List<String> allowedReconViews, String requestedReconView) {
        String incidentView = normalize(incident.getReconView());
        if (incidentView == null) {
            return false;
        }
        if (requestedReconView != null && !requestedReconView.isBlank()) {
            return incidentView.equals(normalize(requestedReconView));
        }
        if (allowedReconViews == null || allowedReconViews.isEmpty()) {
            return true;
        }
        return allowedReconViews.stream()
                .map(this::normalize)
                .anyMatch(incidentView::equals);
    }

    private boolean matchesOutbreakStatus(RegionalIncidentOutbreakDto outbreak, String requestedStatus) {
        if (requestedStatus == null || requestedStatus.isBlank() || "ALL".equalsIgnoreCase(requestedStatus)) {
            return true;
        }
        if ("DETECTED".equalsIgnoreCase(requestedStatus)) {
            return outbreak.isOutbreakDetected();
        }
        return Objects.toString(outbreak.getOutbreakStatus(), "").equalsIgnoreCase(requestedStatus);
    }

    private boolean matchesSearch(RegionalIncidentOutbreakDto outbreak, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String normalized = search.trim().toLowerCase(Locale.ROOT);
        List<String> values = new ArrayList<>();
        values.add(outbreak.getClusterLabel());
        values.add(outbreak.getReconView());
        values.add(outbreak.getIncidentTitle());
        values.add(outbreak.getPriorityReason());
        values.add(outbreak.getOwnerSummary());
        values.add(outbreak.getNextAction());
        if (outbreak.getAffectedStoreIds() != null) {
            values.addAll(outbreak.getAffectedStoreIds());
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(normalized));
    }

    private String outbreakKey(ExceptionStoreIncidentDto incident) {
        ClusterScope cluster = resolveCluster(incident.getStoreId());
        return cluster.key()
                + "::"
                + defaultIfBlank(normalize(incident.getReconView()), "UNKNOWN")
                + "::"
                + defaultIfBlank(normalize(incident.getIncidentTitle()), "GENERAL");
    }

    private ClusterScope resolveCluster(String storeId) {
        String normalized = normalize(storeId);
        if (normalized == null) {
            return new ClusterScope("UNSCOPED", "Unscoped cluster");
        }
        if (normalized.matches("\\d{4,}")) {
            String prefix = normalized.substring(0, 2);
            return new ClusterScope(prefix + "XX", "Cluster " + prefix + "xx");
        }
        int separator = normalized.indexOf('-');
        if (separator > 0) {
            String prefix = normalized.substring(0, separator);
            return new ClusterScope(prefix, prefix + " cluster");
        }
        String prefix = normalized.substring(0, Math.min(3, normalized.length()));
        return new ClusterScope(prefix, prefix + " cluster");
    }

    private String resolveOutbreakStatus(long affectedStores, long overdueCases, long ownershipGapCases) {
        if (affectedStores >= 4 || (affectedStores >= 3 && (overdueCases > 0 || ownershipGapCases > 0))) {
            return "SPREADING";
        }
        if (affectedStores >= 2) {
            return overdueCases == 0 && ownershipGapCases == 0
                    ? "CONTAINED"
                    : "EMERGING";
        }
        return "LOCALIZED";
    }

    private String buildPriorityReason(ExceptionStoreIncidentDto topIncident,
                                       long affectedStores,
                                       long ownershipGapCases,
                                       String outbreakStatus) {
        List<String> reasons = new ArrayList<>();
        reasons.add(affectedStores + " stores impacted");
        if (ownershipGapCases > 0) {
            reasons.add(ownershipGapCases + " ownership gaps");
        }
        if (topIncident.getPriorityReason() != null && !topIncident.getPriorityReason().isBlank()) {
            reasons.add(topIncident.getPriorityReason());
        }
        reasons.add("status " + outbreakStatus.toLowerCase(Locale.ROOT));
        return reasons.stream().limit(3).collect(Collectors.joining(", "));
    }

    private String summarizeOwner(List<ExceptionStoreIncidentDto> incidents) {
        List<String> owners = incidents.stream()
                .map(ExceptionStoreIncidentDto::getOwnerSummary)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (owners.isEmpty()) {
            return "Unassigned";
        }
        if (owners.size() == 1) {
            return owners.get(0);
        }
        return "Mixed ownership";
    }

    private String summarizeNextAction(List<ExceptionStoreIncidentDto> incidents) {
        List<String> actions = incidents.stream()
                .map(ExceptionStoreIncidentDto::getNextAction)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (actions.isEmpty()) {
            return null;
        }
        if (actions.size() == 1) {
            return actions.get(0);
        }
        return "Multiple next actions";
    }

    private boolean isOwnershipGap(String ownershipStatus) {
        String normalized = normalize(ownershipStatus);
        return "UNOWNED".equals(normalized)
                || "NO_NEXT_ACTION".equals(normalized)
                || "OWNERSHIP_GAP".equals(normalized);
    }

    private int outbreakSeverityRank(String outbreakStatus) {
        return switch (Objects.toString(outbreakStatus, "").toUpperCase(Locale.ROOT)) {
            case "SPREADING" -> 4;
            case "EMERGING" -> 3;
            case "CONTAINED" -> 2;
            case "LOCALIZED" -> 1;
            default -> 0;
        };
    }

    private String impactBand(int impactScore) {
        if (impactScore >= 85) {
            return "CRITICAL";
        }
        if (impactScore >= 70) {
            return "HIGH";
        }
        if (impactScore >= 45) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String normalize(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private record ClusterScope(String key, String label) {
    }
}
