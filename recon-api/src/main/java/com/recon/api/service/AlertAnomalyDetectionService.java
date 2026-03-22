package com.recon.api.service;

import com.recon.api.domain.AlertAnomalyRule;
import com.recon.api.domain.AlertEvent;
import com.recon.api.domain.AlertRule;
import com.recon.api.domain.DashboardStats;
import com.recon.api.domain.TenantConfig;
import com.recon.api.repository.AlertAnomalyRuleRepository;
import com.recon.api.repository.AlertEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertAnomalyDetectionService {

    private final AlertAnomalyRuleRepository anomalyRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final TenantService tenantService;
    private final ReconQueryService reconQueryService;
    private final AlertEmailNotificationService alertEmailNotificationService;
    private final AlertWebhookNotificationService alertWebhookNotificationService;
    private final AlertPersonalSubscriptionNotificationService personalSubscriptionNotificationService;
    private final AlertSmsNotificationService alertSmsNotificationService;
    private final AuditLedgerService auditLedgerService;

    @Value("${app.alerting.anomaly.enabled:true}")
    private boolean anomalyEnabled;

    @Scheduled(fixedDelayString = "${app.alerting.anomaly-interval-ms:900000}")
    @Transactional
    public void runAnomalyDetection() {
        if (!anomalyEnabled) {
            return;
        }
        for (AlertAnomalyRule rule : anomalyRuleRepository.findByActiveTrueOrderByUpdatedAtDesc()) {
            try {
                evaluateRule(rule);
            } catch (Exception ex) {
                log.error("Anomaly detection failed for rule {}: {}", rule.getId(), ex.getMessage(), ex);
            }
        }
    }

    @Transactional
    public void runAnomalyDetectionForTenant(String tenantId) {
        anomalyRuleRepository.findByActiveTrueOrderByUpdatedAtDesc().stream()
                .filter(rule -> tenantId.equals(rule.getTenantId()))
                .forEach(rule -> {
                    try {
                        evaluateRule(rule);
                    } catch (Exception ex) {
                        log.error("Manual anomaly detection failed for rule {}: {}", rule.getId(), ex.getMessage(), ex);
                    }
                });
    }

    private void evaluateRule(AlertAnomalyRule rule) {
        MetricSnapshot currentSnapshot = resolveMetricSnapshot(rule, LocalDate.now(), LocalDate.now());
        BaselineSnapshot baselineSnapshot = resolveBaselineSnapshot(rule);
        if (!currentSnapshot.hasData() && baselineSnapshot.validSamples() == 0) {
            alertEventRepository.findLatestActiveByAnomalyRuleIdAndScopeKey(rule.getId(), buildScopeKey(rule))
                    .ifPresent(existing -> {
                        Map<String, Object> beforeState = snapshotEvent(existing);
                        existing.setAlertStatus("RESOLVED");
                        existing.setResolvedBy("system");
                        existing.setResolvedAt(LocalDateTime.now());
                        existing.setEventMessage("Anomaly scan skipped because no source data was available.");
                        alertEventRepository.save(existing);
                        recordEventAudit(existing, "EVENT_AUTO_RESOLVED", "Anomaly alert auto-resolved", existing.getEventMessage(), beforeState);
                    });
            return;
        }

        BigDecimal currentValue = currentSnapshot.value();
        BigDecimal baselineValue = baselineSnapshot.value();
        EvaluationResult result = evaluate(rule, currentValue, baselineValue);
        String scopeKey = buildScopeKey(rule);

        if (result.triggered()) {
            Optional<AlertEvent> activeEvent = alertEventRepository.findLatestActiveByAnomalyRuleIdAndScopeKey(rule.getId(), scopeKey);
            AlertRule notificationRule = buildNotificationRule(rule, result.direction(), baselineValue);
            if (activeEvent.isPresent() && !cooldownExpired(activeEvent.get(), rule)) {
                AlertEvent event = activeEvent.get();
                Map<String, Object> beforeState = snapshotEvent(event);
                refreshEvent(event, rule, result, currentValue, baselineValue);
                recordEventAudit(event, "EVENT_RETRIGGERED", "Anomaly alert retriggered", event.getEventMessage(), beforeState);
                return;
            }

            if (activeEvent.isPresent()) {
                AlertEvent event = activeEvent.get();
                Map<String, Object> beforeState = snapshotEvent(event);
                refreshEvent(event, rule, result, currentValue, baselineValue);
                recordEventAudit(event, "EVENT_RETRIGGERED", "Anomaly alert retriggered", event.getEventMessage(), beforeState);
                alertEmailNotificationService.notifyTriggeredEvent(notificationRule, event, true);
                alertWebhookNotificationService.notifyTriggeredEvent(notificationRule, event, true);
                personalSubscriptionNotificationService.notifyTriggeredEvent(notificationRule, event, true);
                alertSmsNotificationService.notifyTriggeredEvent(notificationRule, event, true);
                return;
            }

            AlertEvent savedEvent = alertEventRepository.save(AlertEvent.builder()
                    .ruleId(null)
                    .anomalyRuleId(rule.getId())
                    .tenantId(rule.getTenantId())
                    .ruleName(rule.getRuleName())
                    .reconView(rule.getReconView())
                    .metricKey(rule.getMetricKey())
                    .severity(rule.getSeverity())
                    .scopeKey(scopeKey)
                    .storeId(rule.getStoreId())
                    .alertStatus("OPEN")
                    .metricValue(currentValue)
                    .thresholdValue(baselineValue)
                    .detectionType("ANOMALY")
                    .anomalyDirection(result.direction())
                    .baselineValue(baselineValue)
                    .deltaPercentage(result.deltaPercentage())
                    .eventMessage(buildMessage(rule, result.direction(), currentValue, baselineValue, result.deltaPercentage()))
                    .triggerCount(1)
                    .firstTriggeredAt(LocalDateTime.now())
                    .lastTriggeredAt(LocalDateTime.now())
                    .build());
            recordEventAudit(savedEvent, "EVENT_OPENED", "Anomaly alert opened", savedEvent.getEventMessage(), null);
            alertEmailNotificationService.notifyTriggeredEvent(notificationRule, savedEvent, false);
            alertWebhookNotificationService.notifyTriggeredEvent(notificationRule, savedEvent, false);
            personalSubscriptionNotificationService.notifyTriggeredEvent(notificationRule, savedEvent, false);
            alertSmsNotificationService.notifyTriggeredEvent(notificationRule, savedEvent, false);
            return;
        }

        alertEventRepository.findLatestActiveByAnomalyRuleIdAndScopeKey(rule.getId(), scopeKey)
                .ifPresent(existing -> {
                    Map<String, Object> beforeState = snapshotEvent(existing);
                    existing.setAlertStatus("RESOLVED");
                    existing.setResolvedBy("system");
                    existing.setResolvedAt(LocalDateTime.now());
                    existing.setMetricValue(currentValue);
                    existing.setThresholdValue(baselineValue);
                    existing.setBaselineValue(baselineValue);
                    existing.setDeltaPercentage(result.deltaPercentage());
                    existing.setEventMessage(buildResolvedMessage(rule, currentValue, baselineValue));
                    alertEventRepository.save(existing);
                    recordEventAudit(existing, "EVENT_AUTO_RESOLVED", "Anomaly alert auto-resolved", existing.getEventMessage(), beforeState);
                });
    }

    private EvaluationResult evaluate(AlertAnomalyRule rule, BigDecimal currentValue, BigDecimal baselineValue) {
        BigDecimal minBaseline = defaultBigDecimal(rule.getMinBaselineValue(), BigDecimal.ONE);
        BigDecimal threshold = defaultBigDecimal(rule.getPercentChangeThreshold(), BigDecimal.valueOf(30));
        if (baselineValue.compareTo(BigDecimal.ZERO) <= 0) {
            if (currentValue.compareTo(minBaseline) >= 0 && allowsSpike(rule.getAnomalyType())) {
                return new EvaluationResult(true, "SPIKE", BigDecimal.valueOf(100));
            }
            return new EvaluationResult(false, null, BigDecimal.ZERO);
        }

        BigDecimal deltaPercentage = currentValue.subtract(baselineValue)
                .multiply(BigDecimal.valueOf(100))
                .divide(baselineValue, 2, RoundingMode.HALF_UP);
        if (allowsSpike(rule.getAnomalyType())
                && deltaPercentage.compareTo(threshold) >= 0
                && currentValue.compareTo(minBaseline) >= 0) {
            return new EvaluationResult(true, "SPIKE", deltaPercentage);
        }
        if (allowsDrop(rule.getAnomalyType())
                && deltaPercentage.compareTo(threshold.negate()) <= 0
                && baselineValue.compareTo(minBaseline) >= 0) {
            return new EvaluationResult(true, "DROP", deltaPercentage);
        }
        return new EvaluationResult(false, null, deltaPercentage);
    }

    private BaselineSnapshot resolveBaselineSnapshot(AlertAnomalyRule rule) {
        int lookbackDays = rule.getLookbackDays() == null || rule.getLookbackDays() <= 0 ? 7 : rule.getLookbackDays();
        BigDecimal sum = BigDecimal.ZERO;
        int samples = 0;
        for (int day = 1; day <= lookbackDays; day++) {
            LocalDate sampleDate = LocalDate.now().minusDays(day);
            MetricSnapshot sample = resolveMetricSnapshot(rule, sampleDate, sampleDate);
            if (sample.hasData()) {
                sum = sum.add(sample.value());
                samples++;
            }
        }
        if (samples == 0) {
            return new BaselineSnapshot(BigDecimal.ZERO, 0);
        }
        return new BaselineSnapshot(sum.divide(BigDecimal.valueOf(samples), 2, RoundingMode.HALF_UP), samples);
    }

    private MetricSnapshot resolveMetricSnapshot(AlertAnomalyRule rule, LocalDate from, LocalDate to) {
        TenantConfig tenant = tenantService.getTenant(rule.getTenantId());
        DashboardStats stats = reconQueryService.getDashboardStats(
                rule.getStoreId() == null ? null : List.of(rule.getStoreId()),
                null,
                from.toString(),
                to.toString(),
                rule.getReconView(),
                tenant
        );
        BigDecimal value = switch (Objects.toString(rule.getMetricKey(), "").toUpperCase(Locale.ROOT)) {
            case "TOTAL_TRANSACTIONS" -> BigDecimal.valueOf(stats.getTotalTransactions());
            case "MATCH_RATE" -> BigDecimal.valueOf(stats.getMatchRate()).setScale(2, RoundingMode.HALF_UP);
            case "MISSING_IN_TARGET" -> BigDecimal.valueOf(stats.getMissingInSiocs());
            case "DUPLICATE_TRANSACTIONS" -> BigDecimal.valueOf(stats.getDuplicateTransactions());
            case "QUANTITY_MISMATCH" -> BigDecimal.valueOf(stats.getQuantityMismatch());
            case "ITEM_MISSING" -> BigDecimal.valueOf(stats.getItemMissing());
            case "TOTAL_MISMATCH" -> BigDecimal.valueOf(stats.getTransactionTotalMismatch());
            default -> BigDecimal.ZERO;
        };
        boolean hasData = stats.getTotalTransactions() > 0
                || (stats.getByStatus() != null && !stats.getByStatus().isEmpty())
                || (stats.getByStore() != null && !stats.getByStore().isEmpty());
        return new MetricSnapshot(value, hasData);
    }

    private void refreshEvent(AlertEvent event,
                              AlertAnomalyRule rule,
                              EvaluationResult result,
                              BigDecimal currentValue,
                              BigDecimal baselineValue) {
        event.setMetricValue(currentValue);
        event.setThresholdValue(baselineValue);
        event.setBaselineValue(baselineValue);
        event.setDetectionType("ANOMALY");
        event.setAnomalyDirection(result.direction());
        event.setDeltaPercentage(result.deltaPercentage());
        event.setEventMessage(buildMessage(rule, result.direction(), currentValue, baselineValue, result.deltaPercentage()));
        event.setLastTriggeredAt(LocalDateTime.now());
        event.setTriggerCount((event.getTriggerCount() == null ? 0 : event.getTriggerCount()) + 1);
        if ("RESOLVED".equalsIgnoreCase(event.getAlertStatus())) {
            event.setAlertStatus("OPEN");
            event.setResolvedAt(null);
            event.setResolvedBy(null);
        }
        alertEventRepository.save(event);
    }

    private boolean cooldownExpired(AlertEvent event, AlertAnomalyRule rule) {
        int cooldownMinutes = rule.getCooldownMinutes() == null ? 180 : rule.getCooldownMinutes();
        if (cooldownMinutes <= 0 || event.getLastTriggeredAt() == null) {
            return true;
        }
        return event.getLastTriggeredAt().isBefore(LocalDateTime.now().minusMinutes(cooldownMinutes));
    }

    private AlertRule buildNotificationRule(AlertAnomalyRule anomalyRule, String direction, BigDecimal baselineValue) {
        return AlertRule.builder()
                .tenantId(anomalyRule.getTenantId())
                .ruleName(anomalyRule.getRuleName())
                .reconView(anomalyRule.getReconView())
                .metricKey(anomalyRule.getMetricKey())
                .operator("DROP".equalsIgnoreCase(direction) ? "<=" : ">=")
                .thresholdValue(baselineValue)
                .severity(anomalyRule.getSeverity())
                .storeId(anomalyRule.getStoreId())
                .cooldownMinutes(anomalyRule.getCooldownMinutes())
                .lookbackDays(anomalyRule.getLookbackDays())
                .active(anomalyRule.isActive())
                .build();
    }

    private String buildScopeKey(AlertAnomalyRule rule) {
        return "%s|%s".formatted(
                rule.getReconView(),
                rule.getStoreId() == null || rule.getStoreId().isBlank() ? "*" : rule.getStoreId()
        );
    }

    private String buildMessage(AlertAnomalyRule rule,
                                String direction,
                                BigDecimal currentValue,
                                BigDecimal baselineValue,
                                BigDecimal deltaPercentage) {
        String scope = rule.getStoreId() == null || rule.getStoreId().isBlank() ? "all stores" : "store " + rule.getStoreId();
        return "%s detected for %s on %s: current %s vs baseline %s (%s%%)".formatted(
                defaultIfBlank(direction, "ANOMALY"),
                prettyMetric(rule.getMetricKey()),
                scope,
                currentValue.stripTrailingZeros().toPlainString(),
                baselineValue.stripTrailingZeros().toPlainString(),
                deltaPercentage.stripTrailingZeros().toPlainString()
        );
    }

    private String buildResolvedMessage(AlertAnomalyRule rule, BigDecimal currentValue, BigDecimal baselineValue) {
        return "%s returned within baseline range at %s vs baseline %s".formatted(
                prettyMetric(rule.getMetricKey()),
                currentValue.stripTrailingZeros().toPlainString(),
                baselineValue.stripTrailingZeros().toPlainString()
        );
    }

    private String prettyMetric(String metricKey) {
        return switch (Objects.toString(metricKey, "").toUpperCase(Locale.ROOT)) {
            case "TOTAL_TRANSACTIONS" -> "Total transactions";
            case "MATCH_RATE" -> "Match rate";
            case "MISSING_IN_TARGET" -> "Missing in target";
            case "DUPLICATE_TRANSACTIONS" -> "Duplicate transactions";
            case "QUANTITY_MISMATCH" -> "Quantity mismatch";
            case "ITEM_MISSING" -> "Item missing";
            case "TOTAL_MISMATCH" -> "Transaction total mismatch";
            default -> metricKey;
        };
    }

    private boolean allowsSpike(String anomalyType) {
        return "SPIKE".equalsIgnoreCase(anomalyType) || "BOTH".equalsIgnoreCase(anomalyType);
    }

    private boolean allowsDrop(String anomalyType) {
        return "DROP".equalsIgnoreCase(anomalyType) || "BOTH".equalsIgnoreCase(anomalyType);
    }

    private BigDecimal defaultBigDecimal(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private void recordEventAudit(AlertEvent event,
                                  String actionType,
                                  String title,
                                  String summary,
                                  Object beforeState) {
        if (event == null || event.getTenantId() == null || event.getTenantId().isBlank()) {
            return;
        }
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(event.getTenantId())
                .sourceType("ALERT")
                .moduleKey(event.getReconView())
                .entityType("ALERT_EVENT")
                .entityKey(event.getId() != null ? event.getId().toString() : event.getScopeKey())
                .actionType(actionType)
                .title(title)
                .summary(summary)
                .actor("system")
                .status(event.getAlertStatus())
                .referenceKey(event.getId() != null ? event.getId().toString() : event.getScopeKey())
                .controlFamily("MONITORING")
                .evidenceTags(List.of("ALERT", "ANOMALY"))
                .beforeState(beforeState)
                .afterState(snapshotEvent(event))
                .eventAt(LocalDateTime.now())
                .build());
    }

    private Map<String, Object> snapshotEvent(AlertEvent event) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", event.getId());
        snapshot.put("ruleId", event.getRuleId());
        snapshot.put("anomalyRuleId", event.getAnomalyRuleId());
        snapshot.put("ruleName", event.getRuleName());
        snapshot.put("reconView", event.getReconView());
        snapshot.put("metricKey", event.getMetricKey());
        snapshot.put("severity", event.getSeverity());
        snapshot.put("scopeKey", event.getScopeKey());
        snapshot.put("storeId", trimToNull(event.getStoreId()));
        snapshot.put("alertStatus", event.getAlertStatus());
        snapshot.put("metricValue", event.getMetricValue());
        snapshot.put("thresholdValue", event.getThresholdValue());
        snapshot.put("detectionType", event.getDetectionType());
        snapshot.put("anomalyDirection", trimToNull(event.getAnomalyDirection()));
        snapshot.put("baselineValue", event.getBaselineValue());
        snapshot.put("deltaPercentage", event.getDeltaPercentage());
        snapshot.put("eventMessage", event.getEventMessage());
        snapshot.put("triggerCount", event.getTriggerCount());
        snapshot.put("firstTriggeredAt", valueOrNull(event.getFirstTriggeredAt()));
        snapshot.put("lastTriggeredAt", valueOrNull(event.getLastTriggeredAt()));
        snapshot.put("acknowledgedBy", trimToNull(event.getAcknowledgedBy()));
        snapshot.put("acknowledgedAt", valueOrNull(event.getAcknowledgedAt()));
        snapshot.put("resolvedBy", trimToNull(event.getResolvedBy()));
        snapshot.put("resolvedAt", valueOrNull(event.getResolvedAt()));
        return snapshot;
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String valueOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    private record MetricSnapshot(BigDecimal value, boolean hasData) {
    }

    private record BaselineSnapshot(BigDecimal value, int validSamples) {
    }

    private record EvaluationResult(boolean triggered, String direction, BigDecimal deltaPercentage) {
    }
}
