package com.recon.api.service;

import com.recon.api.domain.AlertEmailDelivery;
import com.recon.api.domain.AlertEmailSubscription;
import com.recon.api.domain.AlertEvent;
import com.recon.api.domain.AlertRule;
import com.recon.api.domain.Role;
import com.recon.api.domain.User;
import com.recon.api.repository.AlertEmailDeliveryRepository;
import com.recon.api.repository.AlertEmailSubscriptionRepository;
import com.recon.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertEmailNotificationService {

    private static final Map<String, Integer> SEVERITY_RANK = Map.of(
            "LOW", 1,
            "MEDIUM", 2,
            "HIGH", 3,
            "CRITICAL", 4
    );

    private final AlertEmailSubscriptionRepository subscriptionRepository;
    private final AlertEmailDeliveryRepository deliveryRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final AuditLedgerService auditLedgerService;

    @Value("${app.alerting.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.alerting.email.from:no-reply@retailinq.local}")
    private String fromAddress;

    @Value("${app.alerting.email.from-name:RetailINQ Alerts}")
    private String fromName;

    @Value("${app.alerting.email.app-base-url:http://localhost:5173}")
    private String appBaseUrl;

    @Transactional
    public void notifyTriggeredEvent(AlertRule rule, AlertEvent event, boolean repeatedNotification) {
        if (!emailEnabled) {
            return;
        }

        List<AlertEmailSubscription> subscriptions = subscriptionRepository
                .findByTenantIdAndReconViewAndActiveTrueOrderByUpdatedAtDesc(rule.getTenantId(), rule.getReconView())
                .stream()
                .filter(subscription -> matchesMetric(subscription, event))
                .filter(subscription -> matchesSeverity(subscription, event))
                .filter(subscription -> matchesScope(subscription, event))
                .toList();

        if (subscriptions.isEmpty()) {
            return;
        }

        List<User> tenantUsers = userRepository.findByTenantId(rule.getTenantId());
        for (AlertEmailSubscription subscription : subscriptions) {
            Set<String> recipientEmails = resolveRecipientEmails(subscription, tenantUsers);
            if (recipientEmails.isEmpty()) {
                saveDelivery(event, subscription, "unresolved-recipient@retailinq.local", buildSubject(rule, event, repeatedNotification), "SKIPPED", "No active recipient email resolved");
                continue;
            }

            String subject = buildSubject(rule, event, repeatedNotification);
            String body = buildBody(rule, event, repeatedNotification);
            for (String recipientEmail : recipientEmails) {
                sendEmail(event, subscription, recipientEmail, subject, body);
            }
        }
    }

    @Transactional
    public void sendDirectEmail(String tenantId,
                                String reconView,
                                java.util.UUID eventId,
                                String recipientEmail,
                                String subject,
                                String body) {
        if (!emailEnabled || recipientEmail == null || recipientEmail.isBlank()) {
            return;
        }
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(mimeMessage);
            saveDirectDelivery(eventId, tenantId, reconView, recipientEmail, subject, "SENT", null);
        } catch (Exception ex) {
            log.error("Direct alert email delivery failed for event {} to {}: {}", eventId, recipientEmail, ex.getMessage(), ex);
            saveDirectDelivery(eventId, tenantId, reconView, recipientEmail, subject, "FAILED", ex.getMessage());
        }
    }

    private void sendEmail(AlertEvent event,
                           AlertEmailSubscription subscription,
                           String recipientEmail,
                           String subject,
                           String body) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(mimeMessage);
            saveDelivery(event, subscription, recipientEmail, subject, "SENT", null);
        } catch (Exception ex) {
            log.error("Alert email delivery failed for event {} to {}: {}", event.getId(), recipientEmail, ex.getMessage(), ex);
            saveDelivery(event, subscription, recipientEmail, subject, "FAILED", ex.getMessage());
        }
    }

    private void saveDelivery(AlertEvent event,
                              AlertEmailSubscription subscription,
                              String recipientEmail,
                              String subject,
                              String status,
                              String errorMessage) {
        AlertEmailDelivery savedDelivery = deliveryRepository.save(AlertEmailDelivery.builder()
                .eventId(event.getId())
                .subscriptionId(subscription.getId())
                .tenantId(event.getTenantId())
                .reconView(event.getReconView())
                .recipientEmail(recipientEmail)
                .deliveryStatus(status)
                .emailSubject(subject)
                .errorMessage(errorMessage)
                .lastAttemptAt(LocalDateTime.now())
                .deliveredAt("SENT".equalsIgnoreCase(status) ? LocalDateTime.now() : null)
                .build());
        recordDeliveryAudit(savedDelivery,
                event.getId(),
                event.getTenantId(),
                event.getReconView(),
                status,
                recipientEmail,
                subject,
                subscription.getId());
    }

    private void saveDirectDelivery(java.util.UUID eventId,
                                    String tenantId,
                                    String reconView,
                                    String recipientEmail,
                                    String subject,
                                    String status,
                                    String errorMessage) {
        AlertEmailDelivery savedDelivery = deliveryRepository.save(AlertEmailDelivery.builder()
                .eventId(eventId)
                .subscriptionId(null)
                .tenantId(tenantId)
                .reconView(reconView)
                .recipientEmail(recipientEmail)
                .deliveryStatus(status)
                .emailSubject(subject)
                .errorMessage(errorMessage)
                .lastAttemptAt(LocalDateTime.now())
                .deliveredAt("SENT".equalsIgnoreCase(status) ? LocalDateTime.now() : null)
                .build());
        recordDeliveryAudit(savedDelivery,
                eventId,
                tenantId,
                reconView,
                status,
                recipientEmail,
                subject,
                null);
    }

    private boolean matchesMetric(AlertEmailSubscription subscription, AlertEvent event) {
        return subscription.getMetricKey() == null
                || subscription.getMetricKey().isBlank()
                || subscription.getMetricKey().equalsIgnoreCase(event.getMetricKey());
    }

    private boolean matchesSeverity(AlertEmailSubscription subscription, AlertEvent event) {
        String threshold = subscription.getSeverityThreshold();
        if (threshold == null || threshold.isBlank()) {
            return true;
        }
        return SEVERITY_RANK.getOrDefault(event.getSeverity().toUpperCase(Locale.ROOT), 0)
                >= SEVERITY_RANK.getOrDefault(threshold.toUpperCase(Locale.ROOT), 0);
    }

    private boolean matchesScope(AlertEmailSubscription subscription, AlertEvent event) {
        boolean storeMatches = subscription.getStoreId() == null || subscription.getStoreId().isBlank()
                || subscription.getStoreId().equalsIgnoreCase(event.getStoreId());
        boolean wkstnMatches = subscription.getWkstnId() == null || subscription.getWkstnId().isBlank()
                || subscription.getWkstnId().equalsIgnoreCase(event.getWkstnId());
        return storeMatches && wkstnMatches;
    }

    private Set<String> resolveRecipientEmails(AlertEmailSubscription subscription, List<User> tenantUsers) {
        String recipientType = subscription.getRecipientType() == null ? "" : subscription.getRecipientType().trim().toUpperCase(Locale.ROOT);
        String recipientKey = subscription.getRecipientKey() == null ? "" : subscription.getRecipientKey().trim();
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

    private String buildSubject(AlertRule rule, AlertEvent event, boolean repeatedNotification) {
        String prefix = repeatedNotification ? "[Reminder]" : "[Alert]";
        return "%s %s %s on %s".formatted(
                prefix,
                prettyMetric(event.getMetricKey()),
                event.getSeverity(),
                rule.getReconView()
        );
    }

    private String buildBody(AlertRule rule, AlertEvent event, boolean repeatedNotification) {
        StringBuilder body = new StringBuilder();
        body.append(repeatedNotification
                        ? "A previously triggered RetailINQ alert is still active and has breached its notification cooldown."
                        : "A RetailINQ alert threshold has been breached.")
                .append("\n\n")
                .append("Rule: ").append(rule.getRuleName()).append('\n')
                .append("Module: ").append(rule.getReconView()).append('\n')
                .append("Metric: ").append(prettyMetric(event.getMetricKey())).append('\n')
                .append("Severity: ").append(event.getSeverity()).append('\n')
                .append("Scope: ").append(describeScope(event)).append('\n')
                .append("Current Value: ").append(event.getMetricValue()).append('\n')
                .append("Threshold: ").append(rule.getOperator()).append(' ').append(rule.getThresholdValue()).append('\n')
                .append("Status: ").append(event.getAlertStatus()).append('\n')
                .append("Message: ").append(event.getEventMessage()).append('\n')
                .append('\n')
                .append("Open in RetailINQ: ").append(appBaseUrl).append('\n');
        return body.toString();
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

    private void recordDeliveryAudit(AlertEmailDelivery delivery,
                                     java.util.UUID eventId,
                                     String tenantId,
                                     String reconView,
                                     String status,
                                     String recipientEmail,
                                     String subject,
                                     java.util.UUID subscriptionId) {
        if (tenantId == null || tenantId.isBlank() || delivery == null) {
            return;
        }
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("ALERT")
                .moduleKey(reconView == null || reconView.isBlank() ? "ALERTS" : reconView)
                .entityType("ALERT_EMAIL_DELIVERY")
                .entityKey(delivery.getId() != null ? delivery.getId().toString() : Objects.toString(eventId, "alert-email-delivery"))
                .actionType("EMAIL_DELIVERY_" + defaultIfBlank(status, "RECORDED"))
                .title("Alert email delivery " + defaultIfBlank(status, "recorded").toLowerCase(Locale.ROOT))
                .summary("%s -> %s".formatted(defaultIfBlank(subject, "Alert notification"), defaultIfBlank(recipientEmail, "unknown-recipient")))
                .actor("system")
                .status(status)
                .referenceKey(eventId != null ? eventId.toString() : null)
                .controlFamily("MONITORING")
                .evidenceTags(List.of("ALERT", "EMAIL"))
                .afterState(snapshotDelivery(delivery, eventId, recipientEmail, subject, subscriptionId))
                .eventAt(LocalDateTime.now())
                .build());
    }

    private Map<String, Object> snapshotDelivery(AlertEmailDelivery delivery,
                                                 java.util.UUID eventId,
                                                 String recipientEmail,
                                                 String subject,
                                                 java.util.UUID subscriptionId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("deliveryId", delivery.getId());
        snapshot.put("eventId", eventId);
        snapshot.put("subscriptionId", subscriptionId);
        snapshot.put("recipientEmail", recipientEmail);
        snapshot.put("deliveryStatus", delivery.getDeliveryStatus());
        snapshot.put("emailSubject", subject);
        snapshot.put("errorMessage", trimToNull(delivery.getErrorMessage()));
        snapshot.put("createdAt", valueOrNull(delivery.getCreatedAt()));
        snapshot.put("lastAttemptAt", valueOrNull(delivery.getLastAttemptAt()));
        snapshot.put("deliveredAt", valueOrNull(delivery.getDeliveredAt()));
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
