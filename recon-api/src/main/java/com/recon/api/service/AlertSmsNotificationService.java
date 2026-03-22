package com.recon.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.api.domain.AlertEvent;
import com.recon.api.domain.AlertRule;
import com.recon.api.domain.AlertSmsDelivery;
import com.recon.api.domain.AlertSmsSubscription;
import com.recon.api.repository.AlertSmsDeliveryRepository;
import com.recon.api.repository.AlertSmsSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertSmsNotificationService {

    private static final Map<String, Integer> SEVERITY_RANK = Map.of(
            "LOW", 1,
            "MEDIUM", 2,
            "HIGH", 3,
            "CRITICAL", 4
    );

    private final AlertSmsSubscriptionRepository subscriptionRepository;
    private final AlertSmsDeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;
    private final AuditLedgerService auditLedgerService;

    @Value("${app.alerting.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${app.alerting.sms.provider-name:GENERIC_SMS_WEBHOOK}")
    private String providerName;

    @Value("${app.alerting.sms.endpoint-url:}")
    private String endpointUrl;

    @Value("${app.alerting.sms.auth-header-name:}")
    private String authHeaderName;

    @Value("${app.alerting.sms.auth-header-value:}")
    private String authHeaderValue;

    @Value("${app.alerting.sms.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${app.alerting.sms.read-timeout-ms:10000}")
    private int readTimeoutMs;

    @Transactional
    public void notifyTriggeredEvent(AlertRule rule, AlertEvent event, boolean repeatedNotification) {
        List<AlertSmsSubscription> subscriptions = subscriptionRepository
                .findByTenantIdAndReconViewAndActiveTrueOrderByUpdatedAtDesc(rule.getTenantId(), rule.getReconView())
                .stream()
                .filter(subscription -> matchesMetric(subscription, event))
                .filter(subscription -> matchesSeverity(subscription, event))
                .filter(subscription -> matchesScope(subscription, event))
                .toList();

        if (subscriptions.isEmpty()) {
            return;
        }

        for (AlertSmsSubscription subscription : subscriptions) {
            sendSms(rule, event, subscription, repeatedNotification);
        }
    }

    private void sendSms(AlertRule rule,
                         AlertEvent event,
                         AlertSmsSubscription subscription,
                         boolean repeatedNotification) {
        if (!smsEnabled || endpointUrl == null || endpointUrl.isBlank()) {
            saveDelivery(event, subscription, "SKIPPED", null, "SMS delivery is disabled or endpoint is not configured");
            return;
        }

        String payload = serializePayload(rule, event, subscription, repeatedNotification);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.ALL));
        if (authHeaderName != null && !authHeaderName.isBlank() && authHeaderValue != null && !authHeaderValue.isBlank()) {
            headers.set(authHeaderName, authHeaderValue);
        }

        try {
            ResponseEntity<String> response = restTemplate().postForEntity(
                    endpointUrl,
                    new HttpEntity<>(payload, headers),
                    String.class
            );
            saveDelivery(event, subscription, "SENT", response.getStatusCode().value(), null);
        } catch (RestClientException ex) {
            log.error("Alert SMS delivery failed for event {} to {}: {}", event.getId(), subscription.getPhoneNumber(), ex.getMessage(), ex);
            saveDelivery(event, subscription, "FAILED", null, truncate(ex.getMessage()));
        }
    }

    private String serializePayload(AlertRule rule,
                                    AlertEvent event,
                                    AlertSmsSubscription subscription,
                                    boolean repeatedNotification) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", providerName);
        payload.put("phoneNumber", subscription.getPhoneNumber());
        payload.put("message", buildMessage(rule, event, repeatedNotification));
        payload.put("metadata", Map.of(
                "eventId", event.getId(),
                "ruleName", rule.getRuleName(),
                "reconView", rule.getReconView(),
                "metricKey", event.getMetricKey(),
                "severity", event.getSeverity(),
                "storeId", event.getStoreId(),
                "wkstnId", event.getWkstnId()
        ));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize SMS payload", ex);
        }
    }

    private String buildMessage(AlertRule rule, AlertEvent event, boolean repeatedNotification) {
        String prefix = repeatedNotification ? "[Reminder]" : "[Alert]";
        String scope = event.getStoreId() == null || event.getStoreId().isBlank()
                ? "all stores"
                : "store " + event.getStoreId() + (event.getWkstnId() == null || event.getWkstnId().isBlank() ? "" : "/" + event.getWkstnId());
        return "%s %s %s on %s for %s. %s".formatted(
                prefix,
                prettyMetric(event.getMetricKey()),
                event.getSeverity(),
                rule.getReconView(),
                scope,
                truncate(event.getEventMessage())
        );
    }

    private void saveDelivery(AlertEvent event,
                              AlertSmsSubscription subscription,
                              String status,
                              Integer responseStatusCode,
                              String errorMessage) {
        AlertSmsDelivery delivery = deliveryRepository.save(AlertSmsDelivery.builder()
                .eventId(event.getId())
                .subscriptionId(subscription.getId())
                .tenantId(event.getTenantId())
                .reconView(event.getReconView())
                .phoneNumber(subscription.getPhoneNumber())
                .providerName(providerName)
                .deliveryStatus(status)
                .responseStatusCode(responseStatusCode)
                .errorMessage(errorMessage)
                .lastAttemptAt(LocalDateTime.now())
                .deliveredAt("SENT".equalsIgnoreCase(status) ? LocalDateTime.now() : null)
                .build());
        recordDeliveryAudit(delivery, event.getId(), event.getTenantId(), event.getReconView(), subscription.getId());
    }

    private void recordDeliveryAudit(AlertSmsDelivery delivery,
                                     UUID eventId,
                                     String tenantId,
                                     String reconView,
                                     UUID subscriptionId) {
        if (delivery == null || tenantId == null || tenantId.isBlank()) {
            return;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("deliveryId", delivery.getId());
        snapshot.put("eventId", eventId);
        snapshot.put("subscriptionId", subscriptionId);
        snapshot.put("phoneNumber", delivery.getPhoneNumber());
        snapshot.put("providerName", delivery.getProviderName());
        snapshot.put("deliveryStatus", delivery.getDeliveryStatus());
        snapshot.put("responseStatusCode", delivery.getResponseStatusCode());
        snapshot.put("errorMessage", trimToNull(delivery.getErrorMessage()));
        snapshot.put("createdAt", valueOrNull(delivery.getCreatedAt()));
        snapshot.put("lastAttemptAt", valueOrNull(delivery.getLastAttemptAt()));
        snapshot.put("deliveredAt", valueOrNull(delivery.getDeliveredAt()));
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("ALERT")
                .moduleKey(reconView == null || reconView.isBlank() ? "ALERTS" : reconView)
                .entityType("ALERT_SMS_DELIVERY")
                .entityKey(delivery.getId().toString())
                .actionType("SMS_DELIVERY_" + defaultIfBlank(delivery.getDeliveryStatus(), "RECORDED"))
                .title("Alert SMS delivery " + defaultIfBlank(delivery.getDeliveryStatus(), "recorded").toLowerCase(Locale.ROOT))
                .summary("%s -> %s".formatted(defaultIfBlank(delivery.getProviderName(), "SMS"), defaultIfBlank(delivery.getPhoneNumber(), "unknown-number")))
                .actor("system")
                .status(delivery.getDeliveryStatus())
                .referenceKey(eventId == null ? null : eventId.toString())
                .controlFamily("MONITORING")
                .evidenceTags(List.of("ALERT", "SMS"))
                .afterState(snapshot)
                .eventAt(LocalDateTime.now())
                .build());
    }

    private boolean matchesMetric(AlertSmsSubscription subscription, AlertEvent event) {
        return subscription.getMetricKey() == null
                || subscription.getMetricKey().isBlank()
                || subscription.getMetricKey().equalsIgnoreCase(event.getMetricKey());
    }

    private boolean matchesSeverity(AlertSmsSubscription subscription, AlertEvent event) {
        String threshold = subscription.getSeverityThreshold();
        if (threshold == null || threshold.isBlank()) {
            return true;
        }
        return SEVERITY_RANK.getOrDefault(event.getSeverity().toUpperCase(Locale.ROOT), 0)
                >= SEVERITY_RANK.getOrDefault(threshold.toUpperCase(Locale.ROOT), 0);
    }

    private boolean matchesScope(AlertSmsSubscription subscription, AlertEvent event) {
        boolean storeMatches = subscription.getStoreId() == null || subscription.getStoreId().isBlank()
                || subscription.getStoreId().equalsIgnoreCase(event.getStoreId());
        boolean wkstnMatches = subscription.getWkstnId() == null || subscription.getWkstnId().isBlank()
                || subscription.getWkstnId().equalsIgnoreCase(event.getWkstnId());
        return storeMatches && wkstnMatches;
    }

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
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

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.length() <= 280 ? value : value.substring(0, 280);
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
