package com.recon.api.service;

import com.recon.api.domain.AlertEscalationHistory;
import com.recon.api.domain.AlertEscalationPolicy;
import com.recon.api.domain.AlertEvent;
import com.recon.api.domain.AlertRule;
import com.recon.api.domain.Role;
import com.recon.api.domain.User;
import com.recon.api.repository.AlertEscalationHistoryRepository;
import com.recon.api.repository.AlertEscalationPolicyRepository;
import com.recon.api.repository.AlertEventRepository;
import com.recon.api.repository.AlertRuleRepository;
import com.recon.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertEscalationService {

    private static final Set<String> SUPPORTED_EMAIL_DESTINATIONS = Set.of("USER", "ROLE", "EMAIL");
    private static final Set<String> SUPPORTED_WEBHOOK_DESTINATIONS = Set.of("GENERIC_WEBHOOK", "MICROSOFT_TEAMS", "SLACK");
    private static final Map<String, Integer> SEVERITY_RANK = Map.of(
            "LOW", 1,
            "MEDIUM", 2,
            "HIGH", 3,
            "CRITICAL", 4
    );

    private final AlertEscalationPolicyRepository policyRepository;
    private final AlertEscalationHistoryRepository historyRepository;
    private final AlertEventRepository eventRepository;
    private final AlertRuleRepository ruleRepository;
    private final UserRepository userRepository;
    private final AlertEmailNotificationService emailNotificationService;
    private final AlertWebhookNotificationService webhookNotificationService;
    private final AuditLedgerService auditLedgerService;

    @Value("${app.alerting.escalation-enabled:true}")
    private boolean escalationEnabled;

    @Value("${app.alerting.escalation-interval-ms:300000}")
    private long escalationIntervalMs;

    @Value("${app.alerting.webhook.app-base-url:http://localhost:5173}")
    private String appBaseUrl;

    @Scheduled(fixedDelayString = "${app.alerting.escalation-interval-ms:300000}")
    @Transactional
    public void runEscalations() {
        if (!escalationEnabled) {
            return;
        }

        List<AlertEvent> openEvents = eventRepository.findTop250ByAlertStatusInOrderByLastTriggeredAtDesc(List.of("OPEN", "ACKNOWLEDGED"));
        List<AlertEscalationPolicy> activePolicies = policyRepository.findByActiveTrueOrderByUpdatedAtDesc();

        for (AlertEvent event : openEvents) {
            AlertRule rule = ruleRepository.findById(event.getRuleId()).orElse(null);
            if (rule == null) {
                continue;
            }
            activePolicies.stream()
                    .filter(policy -> policy.getTenantId().equals(event.getTenantId()))
                    .filter(policy -> policy.getReconView().equalsIgnoreCase(event.getReconView()))
                    .filter(policy -> matchesMetric(policy, event))
                    .filter(policy -> matchesSeverity(policy, event))
                    .filter(policy -> matchesScope(policy, event))
                    .filter(policy -> shouldEscalate(policy, event))
                    .forEach(policy -> escalate(rule, event, policy));
        }
    }

    private void escalate(AlertRule rule, AlertEvent event, AlertEscalationPolicy policy) {
        if (historyRepository.findByPolicyIdAndEventId(policy.getId(), event.getId()).isPresent()) {
            return;
        }

        boolean success = dispatch(rule, event, policy);
        AlertEscalationHistory savedHistory = historyRepository.save(AlertEscalationHistory.builder()
                .eventId(event.getId())
                .policyId(policy.getId())
                .tenantId(event.getTenantId())
                .reconView(event.getReconView())
                .ruleName(event.getRuleName())
                .severity(event.getSeverity())
                .destinationType(policy.getDestinationType())
                .destinationKey(policy.getDestinationKey())
                .escalationStatus(success ? "SENT" : "FAILED")
                .errorMessage(success ? null : "Escalation delivery failed")
                .escalatedAt(LocalDateTime.now())
                .build());
        recordEscalationAudit(rule, event, policy, savedHistory);
    }

    private boolean dispatch(AlertRule rule, AlertEvent event, AlertEscalationPolicy policy) {
        String destinationType = policy.getDestinationType().toUpperCase(Locale.ROOT);
        if (SUPPORTED_EMAIL_DESTINATIONS.contains(destinationType)) {
            List<String> recipientEmails = resolveRecipientEmails(policy, event.getTenantId());
            if (recipientEmails.isEmpty()) {
                return false;
            }
            String subject = "[Escalation] %s %s on %s".formatted(prettyMetric(event.getMetricKey()), event.getSeverity(), event.getReconView());
            String body = """
                    RetailINQ escalation triggered.

                    Policy: %s
                    Rule: %s
                    Module: %s
                    Metric: %s
                    Severity: %s
                    Scope: %s
                    Current Value: %s
                    Threshold: %s %s
                    Status: %s
                    Message: %s
                    Open in RetailINQ: %s/alerts
                    """.formatted(
                    policy.getPolicyName(),
                    rule.getRuleName(),
                    rule.getReconView(),
                    prettyMetric(event.getMetricKey()),
                    event.getSeverity(),
                    describeScope(event),
                    event.getMetricValue(),
                    rule.getOperator(),
                    rule.getThresholdValue(),
                    event.getAlertStatus(),
                    event.getEventMessage(),
                    appBaseUrl
            );
            recipientEmails.forEach(email -> emailNotificationService.sendDirectEmail(event.getTenantId(), event.getReconView(), event.getId(), email, subject, body));
            return true;
        }

        if (SUPPORTED_WEBHOOK_DESTINATIONS.contains(destinationType)) {
            return webhookNotificationService.sendDirectWebhook(
                    event.getTenantId(),
                    event.getReconView(),
                    event.getId(),
                    destinationType,
                    policy.getDestinationKey(),
                    Map.of(
                            "notificationType", "ESCALATION",
                            "policyName", policy.getPolicyName(),
                            "eventId", event.getId(),
                            "ruleName", event.getRuleName(),
                            "module", event.getReconView(),
                            "metric", prettyMetric(event.getMetricKey()),
                            "severity", event.getSeverity(),
                            "scope", describeScope(event),
                            "message", event.getEventMessage(),
                            "openInRetailInqUrl", appBaseUrl + "/alerts"
                    )
            );
        }

        return false;
    }

    private List<String> resolveRecipientEmails(AlertEscalationPolicy policy, String tenantId) {
        String destinationType = policy.getDestinationType().toUpperCase(Locale.ROOT);
        String destinationKey = policy.getDestinationKey().trim();
        if ("EMAIL".equals(destinationType)) {
            return List.of(destinationKey);
        }
        if ("USER".equals(destinationType)) {
            return userRepository.findByUsernameAndTenantId(destinationKey, tenantId)
                    .filter(User::isActive)
                    .map(User::getEmail)
                    .filter(email -> email != null && !email.isBlank())
                    .map(List::of)
                    .orElse(List.of());
        }
        if ("ROLE".equals(destinationType)) {
            return userRepository.findByTenantId(tenantId).stream()
                    .filter(User::isActive)
                    .filter(user -> user.getRoles().stream().map(Role::getName).anyMatch(role -> destinationKey.equalsIgnoreCase(role)))
                    .map(User::getEmail)
                    .filter(email -> email != null && !email.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private boolean matchesMetric(AlertEscalationPolicy policy, AlertEvent event) {
        return policy.getMetricKey() == null
                || policy.getMetricKey().isBlank()
                || policy.getMetricKey().equalsIgnoreCase(event.getMetricKey());
    }

    private boolean matchesSeverity(AlertEscalationPolicy policy, AlertEvent event) {
        String threshold = policy.getSeverityThreshold();
        if (threshold == null || threshold.isBlank()) {
            return true;
        }
        return SEVERITY_RANK.getOrDefault(event.getSeverity().toUpperCase(Locale.ROOT), 0)
                >= SEVERITY_RANK.getOrDefault(threshold.toUpperCase(Locale.ROOT), 0);
    }

    private boolean matchesScope(AlertEscalationPolicy policy, AlertEvent event) {
        boolean storeMatches = policy.getStoreId() == null || policy.getStoreId().isBlank()
                || policy.getStoreId().equalsIgnoreCase(event.getStoreId());
        boolean wkstnMatches = policy.getWkstnId() == null || policy.getWkstnId().isBlank()
                || policy.getWkstnId().equalsIgnoreCase(event.getWkstnId());
        return storeMatches && wkstnMatches;
    }

    private boolean shouldEscalate(AlertEscalationPolicy policy, AlertEvent event) {
        if (policy.getEscalationAfterMinutes() == null || policy.getEscalationAfterMinutes() < 0) {
            return false;
        }
        LocalDateTime anchor = event.getFirstTriggeredAt() != null ? event.getFirstTriggeredAt() : event.getCreatedAt();
        return anchor != null && Duration.between(anchor, LocalDateTime.now()).toMinutes() >= policy.getEscalationAfterMinutes();
    }

    private String describeScope(AlertEvent event) {
        String store = event.getStoreId() == null || event.getStoreId().isBlank() ? "All stores" : "Store " + event.getStoreId();
        String register = event.getWkstnId() == null || event.getWkstnId().isBlank() ? "" : ", Register " + event.getWkstnId();
        return store + register;
    }

    private String prettyMetric(String metricKey) {
        return switch (metricKey.toUpperCase(Locale.ROOT)) {
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

    private void recordEscalationAudit(AlertRule rule,
                                       AlertEvent event,
                                       AlertEscalationPolicy policy,
                                       AlertEscalationHistory history) {
        if (history == null || history.getTenantId() == null || history.getTenantId().isBlank()) {
            return;
        }
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(history.getTenantId())
                .sourceType("ALERT")
                .moduleKey(history.getReconView())
                .entityType("ALERT_ESCALATION")
                .entityKey(history.getId() != null ? history.getId().toString() : Objects.toString(history.getEventId(), "alert-escalation"))
                .actionType("ESCALATION_" + defaultIfBlank(history.getEscalationStatus(), "RECORDED"))
                .title("Alert escalation " + defaultIfBlank(history.getEscalationStatus(), "recorded").toLowerCase(Locale.ROOT))
                .summary("%s -> %s".formatted(
                        policy != null ? defaultIfBlank(policy.getPolicyName(), "Policy") : "Policy",
                        defaultIfBlank(history.getDestinationType(), "destination")))
                .actor("system")
                .reason(trimToNull(history.getErrorMessage()))
                .status(history.getEscalationStatus())
                .referenceKey(history.getEventId() != null ? history.getEventId().toString() : null)
                .controlFamily("MONITORING")
                .evidenceTags(List.of("ALERT", "ESCALATION"))
                .afterState(snapshotEscalation(rule, event, policy, history))
                .eventAt(history.getEscalatedAt() != null ? history.getEscalatedAt() : LocalDateTime.now())
                .build());
    }

    private Map<String, Object> snapshotEscalation(AlertRule rule,
                                                   AlertEvent event,
                                                   AlertEscalationPolicy policy,
                                                   AlertEscalationHistory history) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("historyId", history.getId());
        snapshot.put("eventId", history.getEventId());
        snapshot.put("policyId", history.getPolicyId());
        snapshot.put("policyName", policy != null ? policy.getPolicyName() : null);
        snapshot.put("ruleName", rule != null ? rule.getRuleName() : history.getRuleName());
        snapshot.put("reconView", history.getReconView());
        snapshot.put("metricKey", event != null ? event.getMetricKey() : null);
        snapshot.put("severity", history.getSeverity());
        snapshot.put("destinationType", history.getDestinationType());
        snapshot.put("destinationKey", trimToNull(history.getDestinationKey()));
        snapshot.put("escalationStatus", history.getEscalationStatus());
        snapshot.put("errorMessage", trimToNull(history.getErrorMessage()));
        snapshot.put("escalatedAt", valueOrNull(history.getEscalatedAt()));
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
}
