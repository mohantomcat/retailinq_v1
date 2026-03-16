package com.recon.api.service;

import com.recon.api.domain.AlertEvent;
import com.recon.api.domain.AlertEventDto;
import com.recon.api.domain.AlertRule;
import com.recon.api.domain.AlertRuleDto;
import com.recon.api.domain.AlertSummaryDto;
import com.recon.api.domain.AlertsResponse;
import com.recon.api.domain.SaveAlertRuleRequest;
import com.recon.api.domain.UpdateAlertEventRequest;
import com.recon.api.repository.AlertEventRepository;
import com.recon.api.repository.AlertRuleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertService {

    private static final Set<String> VALID_RECON_VIEWS = Set.of("XSTORE_SIM", "XSTORE_SIOCS", "XSTORE_XOCS");
    private static final Set<String> VALID_METRICS = Set.of(
            "TOTAL_TRANSACTIONS",
            "MATCH_RATE",
            "MISSING_IN_TARGET",
            "DUPLICATE_TRANSACTIONS",
            "QUANTITY_MISMATCH",
            "ITEM_MISSING",
            "TOTAL_MISMATCH",
            "OPEN_EXCEPTIONS_7_PLUS"
    );
    private static final Set<String> VALID_OPERATORS = Set.of(">", ">=", "<", "<=");
    private static final Set<String> VALID_SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> VALID_EVENT_STATUSES = Set.of("OPEN", "ACKNOWLEDGED", "RESOLVED");

    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;

    @Transactional(readOnly = true)
    public AlertsResponse getAlerts(String tenantId, String reconView, Set<String> allowedReconViews) {
        List<AlertRule> rules = reconView == null || reconView.isBlank()
                ? alertRuleRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId)
                : alertRuleRepository.findByTenantIdAndReconViewOrderByUpdatedAtDesc(tenantId, reconView.toUpperCase());

        List<AlertEvent> events = reconView == null || reconView.isBlank()
                ? alertEventRepository.findTop100ByTenantIdOrderByLastTriggeredAtDesc(tenantId)
                : alertEventRepository.findTop100ByTenantIdAndReconViewOrderByLastTriggeredAtDesc(tenantId, reconView.toUpperCase());

        List<AlertRuleDto> visibleRules = rules.stream()
                .filter(rule -> allowedReconViews.contains(rule.getReconView()))
                .map(this::toDto)
                .collect(Collectors.toList());

        List<AlertEventDto> visibleEvents = events.stream()
                .filter(event -> allowedReconViews.contains(event.getReconView()))
                .map(this::toDto)
                .collect(Collectors.toList());

        return AlertsResponse.builder()
                .summary(AlertSummaryDto.builder()
                        .activeRules(visibleRules.stream().filter(AlertRuleDto::isActive).count())
                        .openEvents(visibleEvents.stream().filter(event -> "OPEN".equalsIgnoreCase(event.getAlertStatus())).count())
                        .acknowledgedEvents(visibleEvents.stream().filter(event -> "ACKNOWLEDGED".equalsIgnoreCase(event.getAlertStatus())).count())
                        .resolvedEvents(visibleEvents.stream().filter(event -> "RESOLVED".equalsIgnoreCase(event.getAlertStatus())).count())
                        .criticalEvents(visibleEvents.stream()
                                .filter(event -> "CRITICAL".equalsIgnoreCase(event.getSeverity()))
                                .filter(event -> !"RESOLVED".equalsIgnoreCase(event.getAlertStatus()))
                                .count())
                        .build())
                .rules(visibleRules)
                .events(visibleEvents)
                .build();
    }

    @Transactional
    public AlertsResponse saveRule(String tenantId,
                                   UUID ruleId,
                                   SaveAlertRuleRequest request,
                                   String username,
                                   Set<String> allowedReconViews) {
        validateRequest(request);
        String reconView = request.getReconView().toUpperCase();
        if (!allowedReconViews.contains(reconView)) {
            throw new IllegalArgumentException("Not allowed to manage alerts for recon view: " + reconView);
        }

        AlertRule rule = ruleId == null
                ? AlertRule.builder().tenantId(tenantId).createdBy(username).build()
                : alertRuleRepository.findById(ruleId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .orElseThrow(() -> new EntityNotFoundException("Alert rule not found"));

        rule.setTenantId(tenantId);
        rule.setRuleName(request.getRuleName().trim());
        rule.setReconView(reconView);
        rule.setMetricKey(request.getMetricKey().toUpperCase());
        rule.setOperator(request.getOperator());
        rule.setThresholdValue(request.getThresholdValue());
        rule.setSeverity(request.getSeverity().toUpperCase());
        rule.setStoreId(trimToNull(request.getStoreId()));
        rule.setWkstnId(trimToNull(request.getWkstnId()));
        rule.setLookbackDays(request.getLookbackDays() == null || request.getLookbackDays() <= 0 ? 1 : request.getLookbackDays());
        rule.setCooldownMinutes(request.getCooldownMinutes() == null || request.getCooldownMinutes() < 0 ? 60 : request.getCooldownMinutes());
        rule.setActive(request.getActive() == null || request.getActive());
        rule.setDescription(trimToNull(request.getDescription()));
        if (rule.getCreatedBy() == null || rule.getCreatedBy().isBlank()) {
            rule.setCreatedBy(username);
        }
        rule.setUpdatedBy(username);

        alertRuleRepository.save(rule);
        return getAlerts(tenantId, null, allowedReconViews);
    }

    @Transactional
    public AlertsResponse deleteRule(String tenantId,
                                     UUID ruleId,
                                     Set<String> allowedReconViews) {
        AlertRule rule = alertRuleRepository.findById(ruleId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .orElseThrow(() -> new EntityNotFoundException("Alert rule not found"));
        alertRuleRepository.delete(rule);
        return getAlerts(tenantId, null, allowedReconViews);
    }

    @Transactional
    public AlertsResponse updateEventStatus(String tenantId,
                                            UUID eventId,
                                            UpdateAlertEventRequest request,
                                            String username,
                                            Set<String> allowedReconViews) {
        if (request == null || request.getStatus() == null || request.getStatus().isBlank()) {
            throw new IllegalArgumentException("Alert event status is required");
        }
        String status = request.getStatus().toUpperCase();
        if (!VALID_EVENT_STATUSES.contains(status) || "OPEN".equals(status)) {
            throw new IllegalArgumentException("Supported event statuses are ACKNOWLEDGED or RESOLVED");
        }

        AlertEvent event = alertEventRepository.findById(eventId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .orElseThrow(() -> new EntityNotFoundException("Alert event not found"));

        if (!allowedReconViews.contains(event.getReconView())) {
            throw new IllegalArgumentException("Not allowed to update alerts for recon view: " + event.getReconView());
        }

        event.setAlertStatus(status);
        if ("ACKNOWLEDGED".equals(status)) {
            event.setAcknowledgedBy(username);
            event.setAcknowledgedAt(java.time.LocalDateTime.now());
        } else {
            event.setResolvedBy(username);
            event.setResolvedAt(java.time.LocalDateTime.now());
        }
        alertEventRepository.save(event);
        return getAlerts(tenantId, null, allowedReconViews);
    }

    private void validateRequest(SaveAlertRuleRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Alert rule request is required");
        }
        if (request.getRuleName() == null || request.getRuleName().isBlank()) {
            throw new IllegalArgumentException("Rule name is required");
        }
        if (request.getReconView() == null || !VALID_RECON_VIEWS.contains(request.getReconView().toUpperCase())) {
            throw new IllegalArgumentException("Valid reconView is required");
        }
        if (request.getMetricKey() == null || !VALID_METRICS.contains(request.getMetricKey().toUpperCase())) {
            throw new IllegalArgumentException("Valid metricKey is required");
        }
        if (request.getOperator() == null || !VALID_OPERATORS.contains(request.getOperator())) {
            throw new IllegalArgumentException("Valid operator is required");
        }
        if (request.getThresholdValue() == null) {
            throw new IllegalArgumentException("Threshold value is required");
        }
        if (request.getSeverity() == null || !VALID_SEVERITIES.contains(request.getSeverity().toUpperCase())) {
            throw new IllegalArgumentException("Valid severity is required");
        }
    }

    private AlertRuleDto toDto(AlertRule rule) {
        return AlertRuleDto.builder()
                .id(rule.getId())
                .ruleName(rule.getRuleName())
                .reconView(rule.getReconView())
                .metricKey(rule.getMetricKey())
                .operator(rule.getOperator())
                .thresholdValue(rule.getThresholdValue())
                .severity(rule.getSeverity())
                .storeId(rule.getStoreId())
                .wkstnId(rule.getWkstnId())
                .lookbackDays(rule.getLookbackDays())
                .cooldownMinutes(rule.getCooldownMinutes())
                .active(rule.isActive())
                .description(rule.getDescription())
                .createdBy(rule.getCreatedBy())
                .updatedBy(rule.getUpdatedBy())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    private AlertEventDto toDto(AlertEvent event) {
        return AlertEventDto.builder()
                .id(event.getId())
                .ruleId(event.getRuleId())
                .ruleName(event.getRuleName())
                .reconView(event.getReconView())
                .metricKey(event.getMetricKey())
                .severity(event.getSeverity())
                .storeId(event.getStoreId())
                .wkstnId(event.getWkstnId())
                .scopeKey(event.getScopeKey())
                .alertStatus(event.getAlertStatus())
                .metricValue(event.getMetricValue())
                .thresholdValue(event.getThresholdValue())
                .eventMessage(event.getEventMessage())
                .triggerCount(event.getTriggerCount())
                .firstTriggeredAt(event.getFirstTriggeredAt())
                .lastTriggeredAt(event.getLastTriggeredAt())
                .acknowledgedBy(event.getAcknowledgedBy())
                .acknowledgedAt(event.getAcknowledgedAt())
                .resolvedBy(event.getResolvedBy())
                .resolvedAt(event.getResolvedAt())
                .build();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
