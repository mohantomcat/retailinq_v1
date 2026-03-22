package com.recon.api.service;

import com.recon.api.domain.AlertDigestRun;
import com.recon.api.domain.AlertDigestSubscription;
import com.recon.api.domain.AlertEscalationHistory;
import com.recon.api.domain.AlertEvent;
import com.recon.api.domain.Role;
import com.recon.api.domain.User;
import com.recon.api.repository.AlertDigestRunRepository;
import com.recon.api.repository.AlertDigestSubscriptionRepository;
import com.recon.api.repository.AlertEmailDeliveryRepository;
import com.recon.api.repository.AlertEscalationHistoryRepository;
import com.recon.api.repository.AlertEventRepository;
import com.recon.api.repository.AlertWebhookDeliveryRepository;
import com.recon.api.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertDigestService {

    private static final Map<String, Integer> SEVERITY_RANK = Map.of(
            "LOW", 1,
            "MEDIUM", 2,
            "HIGH", 3,
            "CRITICAL", 4
    );

    private final AlertDigestSubscriptionRepository digestSubscriptionRepository;
    private final AlertDigestRunRepository digestRunRepository;
    private final AlertEventRepository alertEventRepository;
    private final AlertEscalationHistoryRepository escalationHistoryRepository;
    private final AlertEmailDeliveryRepository emailDeliveryRepository;
    private final AlertWebhookDeliveryRepository webhookDeliveryRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final AuditLedgerService auditLedgerService;

    @Value("${app.alerting.digest.enabled:true}")
    private boolean digestEnabled;

    @Value("${app.alerting.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.alerting.email.from:no-reply@retailinq.local}")
    private String fromAddress;

    @Value("${app.alerting.email.from-name:RetailINQ Alerts}")
    private String fromName;

    @Scheduled(cron = "${app.alerting.digest-cron:0 0 6 * * *}")
    @Transactional
    public void runDailyDigests() {
        if (!digestEnabled) {
            return;
        }
        digestSubscriptionRepository.findByActiveTrueOrderByUpdatedAtDesc()
                .forEach(this::runSingleDigestSafely);
    }

    @Transactional
    public void runDigestsForTenant(String tenantId, UUID subscriptionId) {
        if (subscriptionId != null) {
            digestSubscriptionRepository.findById(subscriptionId)
                    .filter(item -> tenantId.equals(item.getTenantId()))
                    .ifPresent(this::runSingleDigestSafely);
            return;
        }
        digestSubscriptionRepository.findByTenantIdAndActiveTrueOrderByUpdatedAtDesc(tenantId)
                .forEach(this::runSingleDigestSafely);
    }

    private void runSingleDigestSafely(AlertDigestSubscription subscription) {
        try {
            runSingleDigest(subscription);
        } catch (Exception ex) {
            log.error("Daily digest run failed for subscription {}: {}", subscription.getId(), ex.getMessage(), ex);
            saveRun(subscription, "FAILED", 0, buildSubject(subscription, 0, 0, 0), ex.getMessage(), null, null);
        }
    }

    private void runSingleDigest(AlertDigestSubscription subscription) throws Exception {
        List<AlertEvent> openEvents = fetchOpenEvents(subscription).stream()
                .filter(event -> matchesSeverity(subscription.getSeverityThreshold(), event.getSeverity()))
                .filter(event -> matchesScope(subscription, event))
                .toList();

        List<AlertEscalationHistory> escalations = fetchEscalations(subscription).stream()
                .filter(item -> matchesSeverity(subscription.getSeverityThreshold(), item.getSeverity()))
                .toList();

        long failedDeliveries = fetchFailedNotificationCount(subscription);
        long openCount = openEvents.size();
        long anomalyCount = openEvents.stream()
                .filter(event -> "ANOMALY".equalsIgnoreCase(event.getDetectionType()))
                .count();
        String subject = buildSubject(subscription, openCount, anomalyCount, failedDeliveries);
        Set<String> recipients = resolveRecipientEmails(subscription);
        if (!digestEnabled || !emailEnabled) {
            saveRun(subscription, "SKIPPED", openEvents.size(), subject, "Email delivery is disabled", null, recipients);
            return;
        }
        if (recipients.isEmpty()) {
            saveRun(subscription, "SKIPPED", openEvents.size(), subject, "No active recipient email resolved", null, recipients);
            return;
        }

        String body = buildBody(subscription, openEvents, escalations, failedDeliveries);
        for (String recipient : recipients) {
            sendEmail(recipient, subject, body);
        }
        saveRun(subscription, "SENT", openEvents.size(), subject, null, LocalDateTime.now(), recipients);
    }

    private List<AlertEvent> fetchOpenEvents(AlertDigestSubscription subscription) {
        if (subscription.getReconView() == null || subscription.getReconView().isBlank()) {
            return alertEventRepository.findTop100ByTenantIdAndAlertStatusInOrderByLastTriggeredAtDesc(
                    subscription.getTenantId(),
                    List.of("OPEN", "ACKNOWLEDGED")
            );
        }
        return alertEventRepository.findTop100ByTenantIdAndReconViewAndAlertStatusInOrderByLastTriggeredAtDesc(
                subscription.getTenantId(),
                subscription.getReconView(),
                List.of("OPEN", "ACKNOWLEDGED")
        );
    }

    private List<AlertEscalationHistory> fetchEscalations(AlertDigestSubscription subscription) {
        if (subscription.getReconView() == null || subscription.getReconView().isBlank()) {
            return escalationHistoryRepository.findTop100ByTenantIdOrderByEscalatedAtDesc(subscription.getTenantId());
        }
        return escalationHistoryRepository.findTop100ByTenantIdAndReconViewOrderByEscalatedAtDesc(
                subscription.getTenantId(),
                subscription.getReconView()
        );
    }

    private long fetchFailedNotificationCount(AlertDigestSubscription subscription) {
        long failedEmails = (subscription.getReconView() == null || subscription.getReconView().isBlank()
                ? emailDeliveryRepository.findTop100ByTenantIdOrderByCreatedAtDesc(subscription.getTenantId())
                : emailDeliveryRepository.findTop100ByTenantIdAndReconViewOrderByCreatedAtDesc(subscription.getTenantId(), subscription.getReconView()))
                .stream()
                .filter(item -> "FAILED".equalsIgnoreCase(item.getDeliveryStatus()))
                .count();

        long failedWebhooks = (subscription.getReconView() == null || subscription.getReconView().isBlank()
                ? webhookDeliveryRepository.findTop100ByTenantIdOrderByCreatedAtDesc(subscription.getTenantId())
                : webhookDeliveryRepository.findTop100ByTenantIdAndReconViewOrderByCreatedAtDesc(subscription.getTenantId(), subscription.getReconView()))
                .stream()
                .filter(item -> "FAILED".equalsIgnoreCase(item.getDeliveryStatus()))
                .count();

        return failedEmails + failedWebhooks;
    }

    private void sendEmail(String recipient, String subject, String body) throws Exception {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
        helper.setFrom(fromAddress, fromName);
        helper.setTo(recipient);
        helper.setSubject(subject);
        helper.setText(body, false);
        mailSender.send(mimeMessage);
    }

    private AlertDigestRun saveRun(AlertDigestSubscription subscription,
                                   String status,
                                   int itemCount,
                                   String subject,
                                   String errorMessage,
                                   LocalDateTime deliveredAt,
                                   Set<String> recipients) {
        AlertDigestRun run = digestRunRepository.save(AlertDigestRun.builder()
                .subscriptionId(subscription.getId())
                .tenantId(subscription.getTenantId())
                .reconView(subscription.getReconView())
                .scopeType(subscription.getScopeType())
                .scopeKey(subscription.getScopeKey())
                .recipientSummary(recipients == null || recipients.isEmpty() ? subscription.getRecipientType() + ":" + subscription.getRecipientKey() : String.join(", ", recipients))
                .runStatus(status)
                .itemCount(itemCount)
                .digestSubject(subject)
                .errorMessage(errorMessage)
                .deliveredAt(deliveredAt)
                .build());
        recordRunAudit(subscription, run);
        return run;
    }

    private void recordRunAudit(AlertDigestSubscription subscription, AlertDigestRun run) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("runId", run.getId());
        snapshot.put("subscriptionId", subscription.getId());
        snapshot.put("scopeType", run.getScopeType());
        snapshot.put("scopeKey", run.getScopeKey());
        snapshot.put("recipientSummary", run.getRecipientSummary());
        snapshot.put("runStatus", run.getRunStatus());
        snapshot.put("itemCount", run.getItemCount());
        snapshot.put("digestSubject", run.getDigestSubject());
        snapshot.put("errorMessage", run.getErrorMessage());
        snapshot.put("createdAt", run.getCreatedAt());
        snapshot.put("deliveredAt", run.getDeliveredAt());
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(subscription.getTenantId())
                .sourceType("ALERT")
                .moduleKey(subscription.getReconView())
                .entityType("ALERT_DIGEST_RUN")
                .entityKey(run.getId().toString())
                .actionType("DIGEST_" + run.getRunStatus())
                .title("Alert daily digest " + run.getRunStatus().toLowerCase(Locale.ROOT))
                .summary(run.getDigestSubject())
                .actor("system")
                .status(run.getRunStatus())
                .referenceKey(subscription.getId().toString())
                .controlFamily("MONITORING")
                .evidenceTags(List.of("ALERT", "DIGEST"))
                .afterState(snapshot)
                .eventAt(LocalDateTime.now())
                .build());
    }

    private Set<String> resolveRecipientEmails(AlertDigestSubscription subscription) {
        List<User> tenantUsers = userRepository.findByTenantId(subscription.getTenantId());
        String recipientType = Objects.toString(subscription.getRecipientType(), "").trim().toUpperCase(Locale.ROOT);
        String recipientKey = Objects.toString(subscription.getRecipientKey(), "").trim();
        return switch (recipientType) {
            case "EMAIL" -> recipientKey.isBlank() ? Set.of() : Set.of(recipientKey);
            case "USER" -> tenantUsers.stream()
                    .filter(User::isActive)
                    .filter(user -> recipientKey.equalsIgnoreCase(user.getUsername()))
                    .map(User::getEmail)
                    .filter(email -> email != null && !email.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            case "ROLE" -> tenantUsers.stream()
                    .filter(User::isActive)
                    .filter(user -> user.getRoles().stream().map(Role::getName).anyMatch(role -> recipientKey.equalsIgnoreCase(role)))
                    .map(User::getEmail)
                    .filter(email -> email != null && !email.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            default -> Set.of();
        };
    }

    private String buildSubject(AlertDigestSubscription subscription,
                                long openAlerts,
                                long anomalyAlerts,
                                long failedDeliveries) {
        return "[Daily Digest] %s | %s | %d open alerts, %d anomalies, %d failed deliveries".formatted(
                defaultIfBlank(subscription.getReconView(), "ALL_MODULES"),
                scopeLabel(subscription),
                openAlerts,
                anomalyAlerts,
                failedDeliveries
        );
    }

    private String buildBody(AlertDigestSubscription subscription,
                             List<AlertEvent> openEvents,
                             List<AlertEscalationHistory> escalations,
                             long failedDeliveries) {
        long criticalAlerts = openEvents.stream().filter(event -> "CRITICAL".equalsIgnoreCase(event.getSeverity())).count();
        long anomalyAlerts = openEvents.stream().filter(event -> "ANOMALY".equalsIgnoreCase(event.getDetectionType())).count();
        StringBuilder body = new StringBuilder();
        body.append("RetailINQ Daily Alert Digest\n\n")
                .append("Digest: ").append(subscription.getDigestName()).append('\n')
                .append("Module: ").append(defaultIfBlank(subscription.getReconView(), "All modules")).append('\n')
                .append("Scope: ").append(scopeLabel(subscription)).append('\n')
                .append("Severity threshold: ").append(defaultIfBlank(subscription.getSeverityThreshold(), "All severities")).append('\n')
                .append("Open alerts: ").append(openEvents.size()).append('\n')
                .append("Critical alerts: ").append(criticalAlerts).append('\n')
                .append("Open anomalies: ").append(anomalyAlerts).append('\n')
                .append("Escalations: ").append(escalations.size()).append('\n')
                .append("Failed notifications: ").append(failedDeliveries).append("\n\n");

        if (openEvents.isEmpty()) {
            body.append("No open alerts match this digest scope.\n");
        } else {
            body.append("Top open alerts:\n");
            openEvents.stream().limit(10).forEach(event -> body
                    .append("- ")
                    .append(event.getSeverity())
                    .append(" | ")
                    .append(prettyMetric(event.getMetricKey()))
                    .append(" | ")
                    .append(defaultIfBlank(event.getStoreId(), "All stores"))
                    .append(" | ")
                    .append(event.getEventMessage())
                    .append('\n'));
        }

        if (!escalations.isEmpty()) {
            body.append("\nRecent escalations:\n");
            escalations.stream().limit(5).forEach(item -> body
                    .append("- ")
                    .append(item.getSeverity())
                    .append(" | ")
                    .append(item.getRuleName())
                    .append(" -> ")
                    .append(item.getDestinationType())
                    .append(" ")
                    .append(item.getDestinationKey())
                    .append('\n'));
        }
        return body.toString();
    }

    private boolean matchesSeverity(String threshold, String severity) {
        if (threshold == null || threshold.isBlank()) {
            return true;
        }
        return SEVERITY_RANK.getOrDefault(Objects.toString(severity, "").toUpperCase(Locale.ROOT), 0)
                >= SEVERITY_RANK.getOrDefault(threshold.toUpperCase(Locale.ROOT), 0);
    }

    private boolean matchesScope(AlertDigestSubscription subscription, AlertEvent event) {
        String scopeType = Objects.toString(subscription.getScopeType(), "").toUpperCase(Locale.ROOT);
        String scopeKey = Objects.toString(subscription.getScopeKey(), "").trim();
        return switch (scopeType) {
            case "STORE" -> scopeKey.isBlank() || scopeKey.equalsIgnoreCase(event.getStoreId());
            case "REGION" -> scopeKey.isBlank() || scopeKey.equalsIgnoreCase(resolveCluster(event.getStoreId()));
            default -> true;
        };
    }

    private String scopeLabel(AlertDigestSubscription subscription) {
        String scopeType = Objects.toString(subscription.getScopeType(), "").toUpperCase(Locale.ROOT);
        String scopeKey = Objects.toString(subscription.getScopeKey(), "").trim();
        if ("STORE".equals(scopeType)) {
            return scopeKey.isBlank() ? "All stores" : "Store " + scopeKey;
        }
        if ("REGION".equals(scopeType)) {
            return scopeKey.isBlank() ? "All regions" : "Region " + scopeKey;
        }
        if ("ROLE".equals(scopeType)) {
            return scopeKey.isBlank() ? "Role digest" : scopeKey + " role";
        }
        return defaultIfBlank(scopeKey, "All scope");
    }

    private String resolveCluster(String storeId) {
        String normalized = Objects.toString(storeId, "").trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "UNSCOPED";
        }
        if (normalized.matches("\\d{4,}")) {
            return normalized.substring(0, 2) + "XX";
        }
        int separator = normalized.indexOf('-');
        if (separator > 0) {
            return normalized.substring(0, separator);
        }
        return normalized.substring(0, Math.min(3, normalized.length()));
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
            case "OPEN_EXCEPTIONS_7_PLUS" -> "Open exceptions 7+ days";
            default -> metricKey;
        };
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
