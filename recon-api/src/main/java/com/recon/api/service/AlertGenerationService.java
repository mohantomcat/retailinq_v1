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
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertGenerationService {

    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final ReconQueryService reconQueryService;
    private final ExceptionCaseRepository exceptionCaseRepository;
    private final TenantService tenantService;

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
                        .ifPresent(existing -> refreshEvent(existing, metricValue, rule));
                if (alertEventRepository.findLatestActiveByRuleIdAndScopeKey(rule.getId(), scopeKey).isEmpty()) {
                    alertEventRepository.save(AlertEvent.builder()
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
                }
                return;
            }

            refreshEvent(event, metricValue, rule);
            return;
        }

        alertEventRepository.findLatestActiveByRuleIdAndScopeKey(rule.getId(), scopeKey)
                .ifPresent(existing -> {
                    existing.setAlertStatus("RESOLVED");
                    existing.setResolvedBy("system");
                    existing.setResolvedAt(LocalDateTime.now());
                    existing.setMetricValue(metricValue);
                    existing.setEventMessage(buildResolvedMessage(rule, metricValue));
                    alertEventRepository.save(existing);
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
}
