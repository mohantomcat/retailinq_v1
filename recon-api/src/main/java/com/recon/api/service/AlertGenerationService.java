package com.recon.api.service;

import com.recon.api.domain.AlertEvent;
import com.recon.api.domain.AlertRule;
import com.recon.api.domain.DashboardStats;
import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.TenantConfig;
import com.recon.api.repository.AlertEventRepository;
import com.recon.api.repository.AlertRuleRepository;
import com.recon.api.repository.ExceptionCaseRepository;
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
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertGenerationService {

    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final ReconQueryService reconQueryService;
    private final ExceptionCaseRepository exceptionCaseRepository;
    private final TenantService tenantService;
    private final AlertEmailNotificationService alertEmailNotificationService;
    private final AlertWebhookNotificationService alertWebhookNotificationService;
    private final AlertPersonalSubscriptionNotificationService personalSubscriptionNotificationService;
    private final AlertSmsNotificationService alertSmsNotificationService;
    private final AuditLedgerService auditLedgerService;

    @Value("${app.alerting.enabled:true}")
    private boolean alertingEnabled;

    @Scheduled(fixedDelayString = "${app.alerting.generation-interval-ms:300000}")
    @Transactional
    public void generateAlerts() {
        if (!alertingEnabled) {
            return;
        }

        List<AlertRule> rules = alertRuleRepository.findByActiveTrueOrderByUpdatedAtDesc();
        for (AlertRule rule : rules) {
            try {
                evaluateRule(rule);
            } catch (Exception ex) {
                log.error("Alert generation failed for rule {}: {}", rule.getId(), ex.getMessage(), ex);
            }
        }
    }

    private void evaluateRule(AlertRule rule) {
        BigDecimal metricValue = resolveMetricValue(rule);
        boolean triggered = compare(metricValue, rule.getOperator(), rule.getThresholdValue());
        String scopeKey = buildScopeKey(rule);

        if (triggered) {
            AlertEvent event = alertEventRepository.findLatestActiveByRuleIdAndScopeKey(rule.getId(), scopeKey)
                    .filter(existing -> cooldownExpired(existing, rule))
                    .orElse(null);

            if (event == null) {
                alertEventRepository.findLatestActiveByRuleIdAndScopeKey(rule.getId(), scopeKey)
                        .ifPresent(existing -> {
                            Map<String, Object> beforeState = snapshotEvent(existing);
                            refreshEvent(existing, metricValue, rule);
                            recordEventAudit(existing,
                                    "EVENT_RETRIGGERED",
                                    "Alert event retriggered",
                                    existing.getEventMessage(),
                                    "system",
                                    beforeState);
                        });
                if (alertEventRepository.findLatestActiveByRuleIdAndScopeKey(rule.getId(), scopeKey).isEmpty()) {
                    AlertEvent savedEvent = alertEventRepository.save(AlertEvent.builder()
                            .ruleId(rule.getId())
                            .tenantId(rule.getTenantId())
                            .ruleName(rule.getRuleName())
                            .reconView(rule.getReconView())
                            .metricKey(rule.getMetricKey())
                            .severity(rule.getSeverity())
                            .scopeKey(scopeKey)
                            .storeId(rule.getStoreId())
                            .wkstnId(rule.getWkstnId())
                            .alertStatus("OPEN")
                            .metricValue(metricValue)
                            .thresholdValue(rule.getThresholdValue())
                            .eventMessage(buildMessage(rule, metricValue))
                            .triggerCount(1)
                            .firstTriggeredAt(LocalDateTime.now())
                            .lastTriggeredAt(LocalDateTime.now())
                            .build());
                    recordEventAudit(savedEvent,
                            "EVENT_OPENED",
                            "Alert event opened",
                            savedEvent.getEventMessage(),
                            "system",
                            null);
                    alertEmailNotificationService.notifyTriggeredEvent(rule, savedEvent, false);
                    alertWebhookNotificationService.notifyTriggeredEvent(rule, savedEvent, false);
                    personalSubscriptionNotificationService.notifyTriggeredEvent(rule, savedEvent, false);
                    alertSmsNotificationService.notifyTriggeredEvent(rule, savedEvent, false);
                }
                return;
            }

            Map<String, Object> beforeState = snapshotEvent(event);
            refreshEvent(event, metricValue, rule);
            recordEventAudit(event,
                    "EVENT_RETRIGGERED",
                    "Alert event retriggered",
                    event.getEventMessage(),
                    "system",
                    beforeState);
            alertEmailNotificationService.notifyTriggeredEvent(rule, event, true);
            alertWebhookNotificationService.notifyTriggeredEvent(rule, event, true);
            personalSubscriptionNotificationService.notifyTriggeredEvent(rule, event, true);
            alertSmsNotificationService.notifyTriggeredEvent(rule, event, true);
            return;
        }

        alertEventRepository.findLatestActiveByRuleIdAndScopeKey(rule.getId(), scopeKey)
                .ifPresent(existing -> {
                    Map<String, Object> beforeState = snapshotEvent(existing);
                    existing.setAlertStatus("RESOLVED");
                    existing.setResolvedBy("system");
                    existing.setResolvedAt(LocalDateTime.now());
                    existing.setMetricValue(metricValue);
                    existing.setEventMessage(buildResolvedMessage(rule, metricValue));
                    alertEventRepository.save(existing);
                    recordEventAudit(existing,
                            "EVENT_AUTO_RESOLVED",
                            "Alert event auto-resolved",
                            existing.getEventMessage(),
                            "system",
                            beforeState);
                });
    }

    private BigDecimal resolveMetricValue(AlertRule rule) {
        if ("OPEN_EXCEPTIONS_7_PLUS".equalsIgnoreCase(rule.getMetricKey())) {
            List<ExceptionCase> cases = exceptionCaseRepository.findActiveCasesForAging(
                    rule.getTenantId(),
                    rule.getReconView(),
                    LocalDateTime.now().minusDays(30)
            );
            long count = cases.stream()
                    .filter(exceptionCase -> exceptionCase.getUpdatedAt() != null)
                    .filter(exceptionCase -> exceptionCase.getUpdatedAt().toLocalDate().isBefore(LocalDate.now().minusDays(7)))
                    .count();
            return BigDecimal.valueOf(count);
        }

        int lookbackDays = rule.getLookbackDays() == null || rule.getLookbackDays() <= 0 ? 1 : rule.getLookbackDays();
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(lookbackDays - 1L);
        TenantConfig tenant = tenantService.getTenant(rule.getTenantId());
        DashboardStats stats = reconQueryService.getDashboardStats(
                rule.getStoreId() == null ? null : List.of(rule.getStoreId()),
                rule.getWkstnId() == null ? null : List.of(rule.getWkstnId()),
                from.toString(),
                to.toString(),
                rule.getReconView(),
                tenant
        );

        return switch (rule.getMetricKey().toUpperCase()) {
            case "TOTAL_TRANSACTIONS" -> BigDecimal.valueOf(stats.getTotalTransactions());
            case "MATCH_RATE" -> BigDecimal.valueOf(stats.getMatchRate()).setScale(2, RoundingMode.HALF_UP);
            case "MISSING_IN_TARGET" -> BigDecimal.valueOf(stats.getMissingInSiocs());
            case "DUPLICATE_TRANSACTIONS" -> BigDecimal.valueOf(stats.getDuplicateTransactions());
            case "QUANTITY_MISMATCH" -> BigDecimal.valueOf(stats.getQuantityMismatch());
            case "ITEM_MISSING" -> BigDecimal.valueOf(stats.getItemMissing());
            case "TOTAL_MISMATCH" -> BigDecimal.valueOf(stats.getTransactionTotalMismatch());
            default -> BigDecimal.ZERO;
        };
    }

    private boolean compare(BigDecimal metricValue, String operator, BigDecimal thresholdValue) {
        int cmp = metricValue.compareTo(thresholdValue);
        return switch (operator) {
            case ">" -> cmp > 0;
            case ">=" -> cmp >= 0;
            case "<" -> cmp < 0;
            case "<=" -> cmp <= 0;
            default -> false;
        };
    }

    private void refreshEvent(AlertEvent event, BigDecimal metricValue, AlertRule rule) {
        event.setMetricValue(metricValue);
        event.setThresholdValue(rule.getThresholdValue());
        event.setEventMessage(buildMessage(rule, metricValue));
        event.setLastTriggeredAt(LocalDateTime.now());
        event.setTriggerCount((event.getTriggerCount() == null ? 0 : event.getTriggerCount()) + 1);
        if ("RESOLVED".equalsIgnoreCase(event.getAlertStatus())) {
            event.setAlertStatus("OPEN");
            event.setResolvedAt(null);
            event.setResolvedBy(null);
        }
        alertEventRepository.save(event);
    }

    private boolean cooldownExpired(AlertEvent event, AlertRule rule) {
        int cooldownMinutes = rule.getCooldownMinutes() == null ? 60 : rule.getCooldownMinutes();
        if (cooldownMinutes <= 0 || event.getLastTriggeredAt() == null) {
            return true;
        }
        return event.getLastTriggeredAt().isBefore(LocalDateTime.now().minusMinutes(cooldownMinutes));
    }

    private String buildScopeKey(AlertRule rule) {
        return "%s|%s|%s".formatted(
                rule.getReconView(),
                rule.getStoreId() == null ? "*" : rule.getStoreId(),
                rule.getWkstnId() == null ? "*" : rule.getWkstnId()
        );
    }

    private String buildMessage(AlertRule rule, BigDecimal metricValue) {
        String scope = rule.getStoreId() == null
                ? "all stores"
                : "store " + rule.getStoreId() + (rule.getWkstnId() == null ? "" : ", register " + rule.getWkstnId());
        return "%s breached for %s: %s %s %s".formatted(
                prettyMetric(rule.getMetricKey()),
                scope,
                metricValue.stripTrailingZeros().toPlainString(),
                rule.getOperator(),
                rule.getThresholdValue().stripTrailingZeros().toPlainString()
        );
    }

    private String buildResolvedMessage(AlertRule rule, BigDecimal metricValue) {
        return "%s returned within threshold at %s".formatted(
                prettyMetric(rule.getMetricKey()),
                metricValue.stripTrailingZeros().toPlainString()
        );
    }

    private String prettyMetric(String metricKey) {
        return switch (metricKey.toUpperCase()) {
            case "TOTAL_TRANSACTIONS" -> "Total transactions";
            case "MATCH_RATE" -> "Match rate";
            case "MISSING_IN_TARGET" -> "Missing in target";
            case "DUPLICATE_TRANSACTIONS" -> "Duplicate transactions";
            case "QUANTITY_MISMATCH" -> "Quantity mismatch";
            case "ITEM_MISSING" -> "Item missing";
            case "TOTAL_MISMATCH" -> "Transaction total mismatch";
            case "OPEN_EXCEPTIONS_7_PLUS" -> "Open exceptions 7+ days";
            default -> metricKey;
        };
    }

    private void recordEventAudit(AlertEvent event,
                                  String actionType,
                                  String title,
                                  String summary,
                                  String actor,
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
                .actor(actor)
                .status(event.getAlertStatus())
                .referenceKey(event.getId() != null ? event.getId().toString() : event.getScopeKey())
                .controlFamily("MONITORING")
                .evidenceTags(List.of("ALERT", "EVENT"))
                .beforeState(beforeState)
                .afterState(snapshotEvent(event))
                .eventAt(LocalDateTime.now())
                .build());
    }

    private Map<String, Object> snapshotEvent(AlertEvent event) {
        if (event == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", event.getId());
        snapshot.put("ruleId", event.getRuleId());
        snapshot.put("ruleName", event.getRuleName());
        snapshot.put("reconView", event.getReconView());
        snapshot.put("metricKey", event.getMetricKey());
        snapshot.put("severity", event.getSeverity());
        snapshot.put("scopeKey", event.getScopeKey());
        snapshot.put("storeId", trimToNull(event.getStoreId()));
        snapshot.put("wkstnId", trimToNull(event.getWkstnId()));
        snapshot.put("alertStatus", event.getAlertStatus());
        snapshot.put("metricValue", event.getMetricValue());
        snapshot.put("thresholdValue", event.getThresholdValue());
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

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String valueOrNull(Object value) {
        return value == null ? null : value.toString();
    }
}
