package com.recon.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.api.domain.AlertEvent;
import com.recon.api.domain.AlertRule;
import com.recon.api.domain.AlertWebhookDelivery;
import com.recon.api.domain.AlertWebhookSubscription;
import com.recon.api.repository.AlertWebhookDeliveryRepository;
import com.recon.api.repository.AlertWebhookSubscriptionRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertWebhookNotificationService {

    private static final Map<String, Integer> SEVERITY_RANK = Map.of(
            "LOW", 1,
            "MEDIUM", 2,
            "HIGH", 3,
            "CRITICAL", 4
    );

    private final AlertWebhookSubscriptionRepository subscriptionRepository;
    private final AlertWebhookDeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;
    private final AuditLedgerService auditLedgerService;

    @Value("${app.alerting.webhook.enabled:false}")
    private boolean webhookEnabled;

    @Value("${app.alerting.webhook.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${app.alerting.webhook.read-timeout-ms:10000}")
    private int readTimeoutMs;

    @Value("${app.alerting.webhook.app-base-url:http://localhost:5173}")
    private String appBaseUrl;

    @Transactional
    public void notifyTriggeredEvent(AlertRule rule, AlertEvent event, boolean repeatedNotification) {
        if (!webhookEnabled) {
            return;
        }

        List<AlertWebhookSubscription> subscriptions = subscriptionRepository
                .findByTenantIdAndReconViewAndActiveTrueOrderByUpdatedAtDesc(rule.getTenantId(), rule.getReconView())
                .stream()
                .filter(subscription -> matchesMetric(subscription, event))
                .filter(subscription -> matchesSeverity(subscription, event))
                .filter(subscription -> matchesScope(subscription, event))
                .toList();

        if (subscriptions.isEmpty()) {
            return;
        }

        for (AlertWebhookSubscription subscription : subscriptions) {
            sendWebhook(rule, event, subscription, repeatedNotification);
        }
    }

    @Transactional
    public boolean sendDirectWebhook(String tenantId,
                                     String reconView,
                                     java.util.UUID eventId,
                                     String channelType,
                                     String endpointUrl,
                                     Object payloadObject) {
        if (!webhookEnabled || endpointUrl == null || endpointUrl.isBlank()) {
            return false;
        }
        String payload = serializeDirectPayload(payloadObject);
        RestTemplate restTemplate = restTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.ALL));

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    endpointUrl,
                    new HttpEntity<>(payload, headers),
                    String.class
            );
            saveDirectDelivery(eventId, tenantId, reconView, channelType, endpointUrl, payload, response.getStatusCode().value(), truncate(response.getBody()), "SENT", null);
            return true;
        } catch (RestClientException ex) {
            log.error("Direct alert webhook delivery failed for event {} to {}: {}", eventId, endpointUrl, ex.getMessage(), ex);
            saveDirectDelivery(eventId, tenantId, reconView, channelType, endpointUrl, payload, null, null, "FAILED", truncate(ex.getMessage()));
            return false;
        }
    }

    private void sendWebhook(AlertRule rule,
                             AlertEvent event,
                             AlertWebhookSubscription subscription,
                             boolean repeatedNotification) {
        String payload = serializePayload(rule, event, subscription, repeatedNotification);
        RestTemplate restTemplate = restTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.ALL));

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    subscription.getEndpointUrl(),
                    new HttpEntity<>(payload, headers),
                    String.class
            );
            saveDelivery(event, subscription, payload, response.getStatusCode().value(), truncate(response.getBody()), "SENT", null);
        } catch (RestClientException ex) {
            log.error("Alert webhook delivery failed for event {} to {}: {}", event.getId(), subscription.getEndpointUrl(), ex.getMessage(), ex);
            saveDelivery(event, subscription, payload, null, null, "FAILED", truncate(ex.getMessage()));
        }
    }

    private String serializePayload(AlertRule rule,
                                    AlertEvent event,
                                    AlertWebhookSubscription subscription,
                                    boolean repeatedNotification) {
        try {
            return objectMapper.writeValueAsString(buildPayload(rule, event, subscription, repeatedNotification));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize webhook payload", ex);
        }
    }

    private String serializeDirectPayload(Object payloadObject) {
        try {
            return objectMapper.writeValueAsString(payloadObject);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize direct webhook payload", ex);
        }
    }

    private Object buildPayload(AlertRule rule,
                                AlertEvent event,
                                AlertWebhookSubscription subscription,
                                boolean repeatedNotification) {
        return switch (subscription.getChannelType().toUpperCase(Locale.ROOT)) {
            case "MICROSOFT_TEAMS" -> buildTeamsPayload(rule, event, repeatedNotification);
            case "SLACK" -> buildSlackPayload(rule, event, repeatedNotification);
            default -> buildGenericPayload(rule, event, subscription, repeatedNotification);
        };
    }

    private Map<String, Object> buildGenericPayload(AlertRule rule,
                                                    AlertEvent event,
                                                    AlertWebhookSubscription subscription,
                                                    boolean repeatedNotification) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationType", repeatedNotification ? "REMINDER" : "TRIGGERED");
        payload.put("channelType", subscription.getChannelType());
        payload.put("eventId", event.getId());
        payload.put("ruleId", rule.getId());
        payload.put("ruleName", rule.getRuleName());
        payload.put("module", rule.getReconView());
        payload.put("metricKey", event.getMetricKey());
        payload.put("metricLabel", prettyMetric(event.getMetricKey()));
        payload.put("severity", event.getSeverity());
        payload.put("scope", buildScopeMap(event));
        payload.put("metricValue", asNumber(event.getMetricValue()));
        payload.put("threshold", Map.of(
                "operator", rule.getOperator(),
                "value", asNumber(rule.getThresholdValue())
        ));
        payload.put("status", event.getAlertStatus());
        payload.put("message", event.getEventMessage());
        payload.put("firstTriggeredAt", event.getFirstTriggeredAt());
        payload.put("lastTriggeredAt", event.getLastTriggeredAt());
        payload.put("openInRetailInqUrl", appBaseUrl + "/alerts");
        return payload;
    }

    private Map<String, Object> buildTeamsPayload(AlertRule rule,
                                                  AlertEvent event,
                                                  boolean repeatedNotification) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("Module", rule.getReconView());
        facts.put("Metric", prettyMetric(event.getMetricKey()));
        facts.put("Severity", event.getSeverity());
        facts.put("Scope", describeScope(event));
        facts.put("Current Value", event.getMetricValue());
        facts.put("Threshold", rule.getOperator() + " " + rule.getThresholdValue());
        facts.put("Status", event.getAlertStatus());

        return Map.of(
                "@type", "MessageCard",
                "@context", "https://schema.org/extensions",
                "summary", "[RetailINQ] " + rule.getRuleName(),
                "themeColor", teamsColor(event.getSeverity()),
                "title", (repeatedNotification ? "Reminder" : "Alert") + ": " + rule.getRuleName(),
                "sections", List.of(Map.of(
                        "activityTitle", event.getEventMessage(),
                        "facts", facts.entrySet().stream()
                                .map(entry -> Map.of("name", entry.getKey(), "value", String.valueOf(entry.getValue())))
                                .toList(),
                        "markdown", true
                )),
                "potentialAction", List.of(Map.of(
                        "@type", "OpenUri",
                        "name", "Open RetailINQ",
                        "targets", List.of(Map.of("os", "default", "uri", appBaseUrl + "/alerts"))
                ))
        );
    }

    private Map<String, Object> buildSlackPayload(AlertRule rule,
                                                  AlertEvent event,
                                                  boolean repeatedNotification) {
        String heading = "%s: %s".formatted(repeatedNotification ? "Reminder" : "Alert", rule.getRuleName());
        return Map.of(
                "text", heading,
                "blocks", List.of(
                        Map.of(
                                "type", "header",
                                "text", Map.of("type", "plain_text", "text", heading)
                        ),
                        Map.of(
                                "type", "section",
                                "fields", List.of(
                                        slackField("Module", rule.getReconView()),
                                        slackField("Severity", event.getSeverity()),
                                        slackField("Metric", prettyMetric(event.getMetricKey())),
                                        slackField("Scope", describeScope(event)),
                                        slackField("Current Value", String.valueOf(event.getMetricValue())),
                                        slackField("Threshold", rule.getOperator() + " " + rule.getThresholdValue())
                                )
                        ),
                        Map.of(
                                "type", "section",
                                "text", Map.of("type", "mrkdwn", "text", event.getEventMessage())
                        ),
                        Map.of(
                                "type", "actions",
                                "elements", List.of(Map.of(
                                        "type", "button",
                                        "text", Map.of("type", "plain_text", "text", "Open RetailINQ"),
                                        "url", appBaseUrl + "/alerts"
                                ))
                        )
                )
        );
    }

    private Map<String, Object> slackField(String title, String value) {
        return Map.of("type", "mrkdwn", "text", "*%s*\n%s".formatted(title, value));
    }

    private String teamsColor(String severity) {
        return switch (severity == null ? "" : severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> "C62828";
            case "HIGH" -> "EF6C00";
            case "MEDIUM" -> "1565C0";
            default -> "2E7D32";
        };
    }

    private Map<String, Object> buildScopeMap(AlertEvent event) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("storeId", event.getStoreId());
        scope.put("wkstnId", event.getWkstnId());
        scope.put("scopeLabel", describeScope(event));
        return scope;
    }

    private String describeScope(AlertEvent event) {
        String store = event.getStoreId() == null || event.getStoreId().isBlank() ? "All stores" : "Store " + event.getStoreId();
        String register = event.getWkstnId() == null || event.getWkstnId().isBlank() ? "" : ", Register " + event.getWkstnId();
        return store + register;
    }

    private Number asNumber(BigDecimal value) {
        return value == null ? 0 : value.stripTrailingZeros();
    }

    private void saveDelivery(AlertEvent event,
                              AlertWebhookSubscription subscription,
                              String requestPayload,
                              Integer responseStatusCode,
                              String responseBody,
                              String status,
                              String errorMessage) {
        AlertWebhookDelivery savedDelivery = deliveryRepository.save(AlertWebhookDelivery.builder()
                .eventId(event.getId())
                .subscriptionId(subscription.getId())
                .tenantId(event.getTenantId())
                .reconView(event.getReconView())
                .channelType(subscription.getChannelType())
                .endpointUrl(subscription.getEndpointUrl())
                .deliveryStatus(status)
                .responseStatusCode(responseStatusCode)
                .requestPayload(requestPayload)
                .responseBody(responseBody)
                .errorMessage(errorMessage)
                .lastAttemptAt(LocalDateTime.now())
                .deliveredAt("SENT".equalsIgnoreCase(status) ? LocalDateTime.now() : null)
                .build());
        recordDeliveryAudit(savedDelivery,
                event.getId(),
                event.getTenantId(),
                event.getReconView(),
                status,
                subscription.getChannelType(),
                subscription.getEndpointUrl(),
                subscription.getId());
    }

    private void saveDirectDelivery(java.util.UUID eventId,
                                    String tenantId,
                                    String reconView,
                                    String channelType,
                                    String endpointUrl,
                                    String requestPayload,
                                    Integer responseStatusCode,
                                    String responseBody,
                                    String status,
                                    String errorMessage) {
        AlertWebhookDelivery savedDelivery = deliveryRepository.save(AlertWebhookDelivery.builder()
                .eventId(eventId)
                .subscriptionId(null)
                .tenantId(tenantId)
                .reconView(reconView)
                .channelType(channelType)
                .endpointUrl(endpointUrl)
                .deliveryStatus(status)
                .responseStatusCode(responseStatusCode)
                .requestPayload(requestPayload)
                .responseBody(responseBody)
                .errorMessage(errorMessage)
                .lastAttemptAt(LocalDateTime.now())
                .deliveredAt("SENT".equalsIgnoreCase(status) ? LocalDateTime.now() : null)
                .build());
        recordDeliveryAudit(savedDelivery,
                eventId,
                tenantId,
                reconView,
                status,
                channelType,
                endpointUrl,
                null);
    }

    private boolean matchesMetric(AlertWebhookSubscription subscription, AlertEvent event) {
        return subscription.getMetricKey() == null
                || subscription.getMetricKey().isBlank()
                || subscription.getMetricKey().equalsIgnoreCase(event.getMetricKey());
    }

    private boolean matchesSeverity(AlertWebhookSubscription subscription, AlertEvent event) {
        String threshold = subscription.getSeverityThreshold();
        if (threshold == null || threshold.isBlank()) {
            return true;
        }
        return SEVERITY_RANK.getOrDefault(event.getSeverity().toUpperCase(Locale.ROOT), 0)
                >= SEVERITY_RANK.getOrDefault(threshold.toUpperCase(Locale.ROOT), 0);
    }

    private boolean matchesScope(AlertWebhookSubscription subscription, AlertEvent event) {
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

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.length() <= 4000 ? value : value.substring(0, 4000);
    }

    private void recordDeliveryAudit(AlertWebhookDelivery delivery,
                                     java.util.UUID eventId,
                                     String tenantId,
                                     String reconView,
                                     String status,
                                     String channelType,
                                     String endpointUrl,
                                     java.util.UUID subscriptionId) {
        if (tenantId == null || tenantId.isBlank() || delivery == null) {
            return;
        }
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("ALERT")
                .moduleKey(reconView == null || reconView.isBlank() ? "ALERTS" : reconView)
                .entityType("ALERT_WEBHOOK_DELIVERY")
                .entityKey(delivery.getId() != null ? delivery.getId().toString() : Objects.toString(eventId, "alert-webhook-delivery"))
                .actionType("WEBHOOK_DELIVERY_" + defaultIfBlank(status, "RECORDED"))
                .title("Alert webhook delivery " + defaultIfBlank(status, "recorded").toLowerCase(Locale.ROOT))
                .summary("%s -> %s".formatted(defaultIfBlank(channelType, "WEBHOOK"), defaultIfBlank(endpointUrl, "unknown-endpoint")))
                .actor("system")
                .status(status)
                .referenceKey(eventId != null ? eventId.toString() : null)
                .controlFamily("MONITORING")
                .evidenceTags(List.of("ALERT", "WEBHOOK"))
                .afterState(snapshotDelivery(delivery, eventId, channelType, endpointUrl, subscriptionId))
                .eventAt(LocalDateTime.now())
                .build());
    }

    private Map<String, Object> snapshotDelivery(AlertWebhookDelivery delivery,
                                                 java.util.UUID eventId,
                                                 String channelType,
                                                 String endpointUrl,
                                                 java.util.UUID subscriptionId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("deliveryId", delivery.getId());
        snapshot.put("eventId", eventId);
        snapshot.put("subscriptionId", subscriptionId);
        snapshot.put("channelType", channelType);
        snapshot.put("endpointUrl", endpointUrl);
        snapshot.put("deliveryStatus", delivery.getDeliveryStatus());
        snapshot.put("responseStatusCode", delivery.getResponseStatusCode());
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
