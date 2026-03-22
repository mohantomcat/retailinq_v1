package com.recon.api.service;

import com.recon.api.domain.AlertEvent;
import com.recon.api.domain.AlertRule;
import com.recon.api.domain.AlertUserSubscription;
import com.recon.api.domain.User;
import com.recon.api.repository.AlertUserSubscriptionRepository;
import com.recon.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertPersonalSubscriptionNotificationService {

    private static final Map<String, Integer> SEVERITY_RANK = Map.of(
            "LOW", 1,
            "MEDIUM", 2,
            "HIGH", 3,
            "CRITICAL", 4
    );

    private final AlertUserSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final AlertEmailNotificationService emailNotificationService;
    private final AlertWebhookNotificationService webhookNotificationService;

    @Value("${app.alerting.webhook.app-base-url:http://localhost:5173}")
    private String appBaseUrl;

    @Transactional
    public void notifyTriggeredEvent(AlertRule rule, AlertEvent event, boolean repeatedNotification) {
        List<AlertUserSubscription> subscriptions = subscriptionRepository
                .findByTenantIdAndReconViewAndActiveTrueOrderByUpdatedAtDesc(rule.getTenantId(), rule.getReconView())
                .stream()
                .filter(subscription -> matchesMetric(subscription, event))
                .filter(subscription -> matchesSeverity(subscription, event))
                .filter(subscription -> matchesScope(subscription, event))
                .toList();

        for (AlertUserSubscription subscription : subscriptions) {
            userRepository.findById(subscription.getUserId())
                    .filter(User::isActive)
                    .ifPresent(user -> deliver(rule, event, subscription, user, repeatedNotification));
        }
    }

    private void deliver(AlertRule rule,
                         AlertEvent event,
                         AlertUserSubscription subscription,
                         User user,
                         boolean repeatedNotification) {
        String channelType = subscription.getChannelType().toUpperCase(Locale.ROOT);
        if ("EMAIL".equals(channelType)) {
            String subject = "%s %s on %s".formatted(
                    repeatedNotification ? "[Reminder]" : "[Alert]",
                    prettyMetric(event.getMetricKey()),
                    event.getReconView()
            );
            String body = buildEmailBody(rule, event, user.getUsername(), repeatedNotification, "Personal subscription");
            emailNotificationService.sendDirectEmail(event.getTenantId(), event.getReconView(), event.getId(), user.getEmail(), subject, body);
            return;
        }

        if (subscription.getEndpointUrl() == null || subscription.getEndpointUrl().isBlank()) {
            log.warn("Skipping personal webhook subscription {} for user {} due to missing endpoint", subscription.getId(), user.getUsername());
            return;
        }

        webhookNotificationService.sendDirectWebhook(
                event.getTenantId(),
                event.getReconView(),
                event.getId(),
                channelType,
                subscription.getEndpointUrl(),
                buildWebhookPayload(rule, event, user.getUsername(), repeatedNotification, channelType)
        );
    }

    private boolean matchesMetric(AlertUserSubscription subscription, AlertEvent event) {
        return subscription.getMetricKey() == null
                || subscription.getMetricKey().isBlank()
                || subscription.getMetricKey().equalsIgnoreCase(event.getMetricKey());
    }

    private boolean matchesSeverity(AlertUserSubscription subscription, AlertEvent event) {
        String threshold = subscription.getSeverityThreshold();
        if (threshold == null || threshold.isBlank()) {
            return true;
        }
        return SEVERITY_RANK.getOrDefault(event.getSeverity().toUpperCase(Locale.ROOT), 0)
                >= SEVERITY_RANK.getOrDefault(threshold.toUpperCase(Locale.ROOT), 0);
    }

    private boolean matchesScope(AlertUserSubscription subscription, AlertEvent event) {
        boolean storeMatches = subscription.getStoreId() == null || subscription.getStoreId().isBlank()
                || subscription.getStoreId().equalsIgnoreCase(event.getStoreId());
        boolean wkstnMatches = subscription.getWkstnId() == null || subscription.getWkstnId().isBlank()
                || subscription.getWkstnId().equalsIgnoreCase(event.getWkstnId());
        return storeMatches && wkstnMatches;
    }

    private String buildEmailBody(AlertRule rule,
                                  AlertEvent event,
                                  String username,
                                  boolean repeatedNotification,
                                  String label) {
        return "%s notification for %s\n\nUser: %s\nRule: %s\nModule: %s\nMetric: %s\nSeverity: %s\nScope: %s\nCurrent Value: %s\nThreshold: %s %s\nStatus: %s\nMessage: %s\nGenerated At: %s\nOpen in RetailINQ: %s/alerts"
                .formatted(
                        label,
                        repeatedNotification ? "an active repeated alert" : "a newly triggered alert",
                        username,
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
                        LocalDateTime.now(),
                        appBaseUrl
                );
    }

    private Object buildWebhookPayload(AlertRule rule,
                                       AlertEvent event,
                                       String username,
                                       boolean repeatedNotification,
                                       String channelType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationType", repeatedNotification ? "PERSONAL_REMINDER" : "PERSONAL_TRIGGERED");
        payload.put("recipient", username);
        payload.put("channelType", channelType);
        payload.put("eventId", event.getId());
        payload.put("ruleName", rule.getRuleName());
        payload.put("module", rule.getReconView());
        payload.put("metric", prettyMetric(event.getMetricKey()));
        payload.put("severity", event.getSeverity());
        payload.put("scope", describeScope(event));
        payload.put("metricValue", event.getMetricValue());
        payload.put("threshold", "%s %s".formatted(rule.getOperator(), rule.getThresholdValue()));
        payload.put("message", event.getEventMessage());
        payload.put("openInRetailInqUrl", appBaseUrl + "/alerts");
        return payload;
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
}
