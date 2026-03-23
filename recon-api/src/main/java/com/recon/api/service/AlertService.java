package com.recon.api.service;

import com.recon.api.domain.AlertAnomalyRule;
import com.recon.api.domain.AlertAnomalyRuleDto;
import com.recon.api.domain.AlertDigestRun;
import com.recon.api.domain.AlertDigestRunDto;
import com.recon.api.domain.AlertDigestSubscription;
import com.recon.api.domain.AlertDigestSubscriptionDto;
import com.recon.api.domain.AlertEvent;
import com.recon.api.domain.AlertEmailDelivery;
import com.recon.api.domain.AlertEmailDeliveryDto;
import com.recon.api.domain.AlertEmailSubscription;
import com.recon.api.domain.AlertEmailSubscriptionDto;
import com.recon.api.domain.AlertEscalationHistory;
import com.recon.api.domain.AlertEscalationHistoryDto;
import com.recon.api.domain.AlertEscalationPolicy;
import com.recon.api.domain.AlertEscalationPolicyDto;
import com.recon.api.domain.AlertEventDto;
import com.recon.api.domain.AlertRule;
import com.recon.api.domain.AlertRuleDto;
import com.recon.api.domain.AlertSmsDelivery;
import com.recon.api.domain.AlertSmsDeliveryDto;
import com.recon.api.domain.AlertSmsSubscription;
import com.recon.api.domain.AlertSmsSubscriptionDto;
import com.recon.api.domain.AlertSummaryDto;
import com.recon.api.domain.AlertUserSubscription;
import com.recon.api.domain.AlertUserSubscriptionDto;
import com.recon.api.domain.AlertWebhookDelivery;
import com.recon.api.domain.AlertWebhookDeliveryDto;
import com.recon.api.domain.AlertWebhookSubscription;
import com.recon.api.domain.AlertWebhookSubscriptionDto;
import com.recon.api.domain.AlertsResponse;
import com.recon.api.domain.SaveAlertAnomalyRuleRequest;
import com.recon.api.domain.SaveAlertDigestSubscriptionRequest;
import com.recon.api.domain.SaveAlertEscalationPolicyRequest;
import com.recon.api.domain.SaveAlertRuleRequest;
import com.recon.api.domain.SaveAlertEmailSubscriptionRequest;
import com.recon.api.domain.SaveAlertSmsSubscriptionRequest;
import com.recon.api.domain.SaveAlertUserSubscriptionRequest;
import com.recon.api.domain.SaveAlertWebhookSubscriptionRequest;
import com.recon.api.domain.User;
import com.recon.api.domain.UpdateAlertEventRequest;
import com.recon.api.repository.AlertAnomalyRuleRepository;
import com.recon.api.repository.AlertDigestRunRepository;
import com.recon.api.repository.AlertDigestSubscriptionRepository;
import com.recon.api.repository.AlertEscalationHistoryRepository;
import com.recon.api.repository.AlertEscalationPolicyRepository;
import com.recon.api.repository.AlertEmailDeliveryRepository;
import com.recon.api.repository.AlertEmailSubscriptionRepository;
import com.recon.api.repository.AlertEventRepository;
import com.recon.api.repository.AlertRuleRepository;
import com.recon.api.repository.AlertSmsDeliveryRepository;
import com.recon.api.repository.AlertSmsSubscriptionRepository;
import com.recon.api.repository.AlertUserSubscriptionRepository;
import com.recon.api.repository.AlertWebhookDeliveryRepository;
import com.recon.api.repository.AlertWebhookSubscriptionRepository;
import com.recon.api.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertService {

    private static final Set<String> VALID_RECON_VIEWS = Set.of("XSTORE_SIM", "XSTORE_SIOCS", "XSTORE_XOCS", "SIOCS_MFCS");
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
    private static final Set<String> VALID_RECIPIENT_TYPES = Set.of("USER", "ROLE", "EMAIL");
    private static final Set<String> VALID_WEBHOOK_CHANNEL_TYPES = Set.of("GENERIC_WEBHOOK", "MICROSOFT_TEAMS", "SLACK");
    private static final Set<String> VALID_ESCALATION_DESTINATION_TYPES = Set.of("USER", "ROLE", "EMAIL", "GENERIC_WEBHOOK", "MICROSOFT_TEAMS", "SLACK");
    private static final Set<String> VALID_PERSONAL_CHANNEL_TYPES = Set.of("EMAIL", "GENERIC_WEBHOOK", "MICROSOFT_TEAMS", "SLACK");
    private static final Set<String> VALID_DIGEST_SCOPE_TYPES = Set.of("ROLE", "STORE", "REGION");
    private static final Set<String> VALID_ANOMALY_TYPES = Set.of("SPIKE", "DROP", "BOTH");

    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final AlertEmailSubscriptionRepository alertEmailSubscriptionRepository;
    private final AlertEmailDeliveryRepository alertEmailDeliveryRepository;
    private final AlertWebhookSubscriptionRepository alertWebhookSubscriptionRepository;
    private final AlertWebhookDeliveryRepository alertWebhookDeliveryRepository;
    private final AlertEscalationPolicyRepository alertEscalationPolicyRepository;
    private final AlertEscalationHistoryRepository alertEscalationHistoryRepository;
    private final AlertUserSubscriptionRepository alertUserSubscriptionRepository;
    private final AlertDigestSubscriptionRepository alertDigestSubscriptionRepository;
    private final AlertDigestRunRepository alertDigestRunRepository;
    private final AlertAnomalyRuleRepository alertAnomalyRuleRepository;
    private final AlertSmsSubscriptionRepository alertSmsSubscriptionRepository;
    private final AlertSmsDeliveryRepository alertSmsDeliveryRepository;
    private final UserRepository userRepository;
    private final AuditLedgerService auditLedgerService;

    @Transactional(readOnly = true)
    public AlertsResponse getAlerts(String tenantId, String userId, String username, String reconView, Set<String> allowedReconViews) {
        List<AlertRule> rules = reconView == null || reconView.isBlank()
                ? alertRuleRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId)
                : alertRuleRepository.findByTenantIdAndReconViewOrderByUpdatedAtDesc(tenantId, reconView.toUpperCase());

        List<AlertEvent> events = reconView == null || reconView.isBlank()
                ? alertEventRepository.findTop100ByTenantIdOrderByLastTriggeredAtDesc(tenantId)
                : alertEventRepository.findTop100ByTenantIdAndReconViewOrderByLastTriggeredAtDesc(tenantId, reconView.toUpperCase());

        List<AlertEmailSubscription> subscriptions = reconView == null || reconView.isBlank()
                ? alertEmailSubscriptionRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId)
                : alertEmailSubscriptionRepository.findByTenantIdAndReconViewOrderByUpdatedAtDesc(tenantId, reconView.toUpperCase());

        List<AlertEmailDelivery> deliveries = reconView == null || reconView.isBlank()
                ? alertEmailDeliveryRepository.findTop100ByTenantIdOrderByCreatedAtDesc(tenantId)
                : alertEmailDeliveryRepository.findTop100ByTenantIdAndReconViewOrderByCreatedAtDesc(tenantId, reconView.toUpperCase());

        List<AlertWebhookSubscription> webhookSubscriptions = reconView == null || reconView.isBlank()
                ? alertWebhookSubscriptionRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId)
                : alertWebhookSubscriptionRepository.findByTenantIdAndReconViewOrderByUpdatedAtDesc(tenantId, reconView.toUpperCase());

        List<AlertWebhookDelivery> webhookDeliveries = reconView == null || reconView.isBlank()
                ? alertWebhookDeliveryRepository.findTop100ByTenantIdOrderByCreatedAtDesc(tenantId)
                : alertWebhookDeliveryRepository.findTop100ByTenantIdAndReconViewOrderByCreatedAtDesc(tenantId, reconView.toUpperCase());

        List<AlertEscalationPolicy> escalationPolicies = reconView == null || reconView.isBlank()
                ? alertEscalationPolicyRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId)
                : alertEscalationPolicyRepository.findByTenantIdAndReconViewOrderByUpdatedAtDesc(tenantId, reconView.toUpperCase());

        List<AlertEscalationHistory> escalationHistory = reconView == null || reconView.isBlank()
                ? alertEscalationHistoryRepository.findTop100ByTenantIdOrderByEscalatedAtDesc(tenantId)
                : alertEscalationHistoryRepository.findTop100ByTenantIdAndReconViewOrderByEscalatedAtDesc(tenantId, reconView.toUpperCase());

        List<AlertUserSubscription> personalSubscriptions = userId == null || userId.isBlank()
                ? List.of()
                : alertUserSubscriptionRepository.findByTenantIdAndUserIdOrderByUpdatedAtDesc(tenantId, UUID.fromString(userId));

        List<AlertDigestSubscription> digestSubscriptions = reconView == null || reconView.isBlank()
                ? alertDigestSubscriptionRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId)
                : alertDigestSubscriptionRepository.findByTenantIdAndReconViewOrderByUpdatedAtDesc(tenantId, reconView.toUpperCase());

        List<AlertDigestRun> digestRuns = reconView == null || reconView.isBlank()
                ? alertDigestRunRepository.findTop100ByTenantIdOrderByCreatedAtDesc(tenantId)
                : alertDigestRunRepository.findTop100ByTenantIdAndReconViewOrderByCreatedAtDesc(tenantId, reconView.toUpperCase());

        List<AlertAnomalyRule> anomalyRules = reconView == null || reconView.isBlank()
                ? alertAnomalyRuleRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId)
                : alertAnomalyRuleRepository.findByTenantIdAndReconViewOrderByUpdatedAtDesc(tenantId, reconView.toUpperCase());

        List<AlertSmsSubscription> smsSubscriptions = reconView == null || reconView.isBlank()
                ? alertSmsSubscriptionRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId)
                : alertSmsSubscriptionRepository.findByTenantIdAndReconViewOrderByUpdatedAtDesc(tenantId, reconView.toUpperCase());

        List<AlertSmsDelivery> smsDeliveries = reconView == null || reconView.isBlank()
                ? alertSmsDeliveryRepository.findTop100ByTenantIdOrderByCreatedAtDesc(tenantId)
                : alertSmsDeliveryRepository.findTop100ByTenantIdAndReconViewOrderByCreatedAtDesc(tenantId, reconView.toUpperCase());

        List<AlertRuleDto> visibleRules = rules.stream()
                .filter(rule -> allowedReconViews.contains(rule.getReconView()))
                .map(this::toDto)
                .collect(Collectors.toList());

        List<AlertEventDto> visibleEvents = events.stream()
                .filter(event -> allowedReconViews.contains(event.getReconView()))
                .map(this::toDto)
                .collect(Collectors.toList());

        List<AlertEmailSubscriptionDto> visibleSubscriptions = subscriptions.stream()
                .filter(subscription -> allowedReconViews.contains(subscription.getReconView()))
                .map(this::toDto)
                .collect(Collectors.toList());

        List<AlertEmailDeliveryDto> visibleDeliveries = deliveries.stream()
                .filter(delivery -> allowedReconViews.contains(delivery.getReconView()))
                .map(this::toDto)
                .collect(Collectors.toList());

        List<AlertWebhookSubscriptionDto> visibleWebhookSubscriptions = webhookSubscriptions.stream()
                .filter(subscription -> allowedReconViews.contains(subscription.getReconView()))
                .map(this::toDto)
                .collect(Collectors.toList());

        List<AlertWebhookDeliveryDto> visibleWebhookDeliveries = webhookDeliveries.stream()
                .filter(delivery -> allowedReconViews.contains(delivery.getReconView()))
                .map(this::toDto)
                .collect(Collectors.toList());

        List<AlertEscalationPolicyDto> visibleEscalationPolicies = escalationPolicies.stream()
                .filter(policy -> allowedReconViews.contains(policy.getReconView()))
                .map(this::toDto)
                .collect(Collectors.toList());

        List<AlertEscalationHistoryDto> visibleEscalationHistory = escalationHistory.stream()
                .filter(item -> allowedReconViews.contains(item.getReconView()))
                .map(this::toDto)
                .collect(Collectors.toList());

        List<AlertUserSubscriptionDto> visiblePersonalSubscriptions = personalSubscriptions.stream()
                .filter(subscription -> allowedReconViews.contains(subscription.getReconView()))
                .map(this::toDto)
                .collect(Collectors.toList());

        List<AlertDigestSubscriptionDto> visibleDigestSubscriptions = digestSubscriptions.stream()
                .filter(subscription -> allowedReconViews.contains(subscription.getReconView()))
                .map(this::toDto)
                .collect(Collectors.toList());

        List<AlertDigestRunDto> visibleDigestRuns = digestRuns.stream()
                .filter(run -> allowedReconViews.contains(run.getReconView()))
                .map(this::toDto)
                .collect(Collectors.toList());

        List<AlertAnomalyRuleDto> visibleAnomalyRules = anomalyRules.stream()
                .filter(rule -> allowedReconViews.contains(rule.getReconView()))
                .map(this::toDto)
                .collect(Collectors.toList());

        List<AlertSmsSubscriptionDto> visibleSmsSubscriptions = smsSubscriptions.stream()
                .filter(subscription -> allowedReconViews.contains(subscription.getReconView()))
                .map(this::toDto)
                .collect(Collectors.toList());

        List<AlertSmsDeliveryDto> visibleSmsDeliveries = smsDeliveries.stream()
                .filter(delivery -> allowedReconViews.contains(delivery.getReconView()))
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
                        .activeSubscriptions(visibleSubscriptions.stream().filter(AlertEmailSubscriptionDto::isActive).count())
                        .activeWebhookSubscriptions(visibleWebhookSubscriptions.stream().filter(AlertWebhookSubscriptionDto::isActive).count())
                        .activeEscalationPolicies(visibleEscalationPolicies.stream().filter(AlertEscalationPolicyDto::isActive).count())
                        .activePersonalSubscriptions(visiblePersonalSubscriptions.stream().filter(AlertUserSubscriptionDto::isActive).count())
                        .activeDigestSubscriptions(visibleDigestSubscriptions.stream().filter(AlertDigestSubscriptionDto::isActive).count())
                        .recentDigestRuns(visibleDigestRuns.stream().filter(run -> "SENT".equalsIgnoreCase(run.getRunStatus())).count())
                        .activeAnomalyRules(visibleAnomalyRules.stream().filter(AlertAnomalyRuleDto::isActive).count())
                        .openAnomalyEvents(visibleEvents.stream()
                                .filter(event -> "ANOMALY".equalsIgnoreCase(event.getDetectionType()))
                                .filter(event -> !"RESOLVED".equalsIgnoreCase(event.getAlertStatus()))
                                .count())
                        .activeSmsSubscriptions(visibleSmsSubscriptions.stream().filter(AlertSmsSubscriptionDto::isActive).count())
                        .escalatedEvents(visibleEscalationHistory.stream().map(AlertEscalationHistoryDto::getEventId).distinct().count())
                        .sentEmailDeliveries(visibleDeliveries.stream().filter(delivery -> "SENT".equalsIgnoreCase(delivery.getDeliveryStatus())).count())
                        .failedEmailDeliveries(visibleDeliveries.stream().filter(delivery -> "FAILED".equalsIgnoreCase(delivery.getDeliveryStatus())).count())
                        .sentWebhookDeliveries(visibleWebhookDeliveries.stream().filter(delivery -> "SENT".equalsIgnoreCase(delivery.getDeliveryStatus())).count())
                        .failedWebhookDeliveries(visibleWebhookDeliveries.stream().filter(delivery -> "FAILED".equalsIgnoreCase(delivery.getDeliveryStatus())).count())
                        .sentSmsDeliveries(visibleSmsDeliveries.stream().filter(delivery -> "SENT".equalsIgnoreCase(delivery.getDeliveryStatus())).count())
                        .failedSmsDeliveries(visibleSmsDeliveries.stream().filter(delivery -> "FAILED".equalsIgnoreCase(delivery.getDeliveryStatus())).count())
                        .failedNotificationDeliveries(
                                visibleDeliveries.stream().filter(delivery -> "FAILED".equalsIgnoreCase(delivery.getDeliveryStatus())).count()
                                        + visibleWebhookDeliveries.stream().filter(delivery -> "FAILED".equalsIgnoreCase(delivery.getDeliveryStatus())).count()
                                        + visibleSmsDeliveries.stream().filter(delivery -> "FAILED".equalsIgnoreCase(delivery.getDeliveryStatus())).count())
                        .build())
                .rules(visibleRules)
                .events(visibleEvents)
                .subscriptions(visibleSubscriptions)
                .deliveries(visibleDeliveries)
                .webhookSubscriptions(visibleWebhookSubscriptions)
                .webhookDeliveries(visibleWebhookDeliveries)
                .escalationPolicies(visibleEscalationPolicies)
                .escalationHistory(visibleEscalationHistory)
                .personalSubscriptions(visiblePersonalSubscriptions)
                .digestSubscriptions(visibleDigestSubscriptions)
                .digestRuns(visibleDigestRuns)
                .anomalyRules(visibleAnomalyRules)
                .smsSubscriptions(visibleSmsSubscriptions)
                .smsDeliveries(visibleSmsDeliveries)
                .build();
    }

    @Transactional
    public AlertsResponse saveRule(String tenantId,
                                   String userId,
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
        Map<String, Object> beforeState = snapshotRule(ruleId == null ? null : rule);

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

        AlertRule savedRule = alertRuleRepository.save(rule);
        recordAlertAudit(savedRule.getTenantId(),
                savedRule.getReconView(),
                "ALERT_RULE",
                savedRule.getId(),
                ruleId == null ? "RULE_CREATED" : "RULE_UPDATED",
                ruleId == null ? "Alert rule created" : "Alert rule updated",
                "%s | %s".formatted(savedRule.getRuleName(), savedRule.getMetricKey()),
                username,
                savedRule.isActive() ? "ACTIVE" : "INACTIVE",
                null,
                beforeState,
                snapshotRule(savedRule),
                List.of("ALERT", "RULE"));
        return getAlerts(tenantId, userId, username, null, allowedReconViews);
    }

    @Transactional
    public AlertsResponse deleteRule(String tenantId,
                                     String userId,
                                     String username,
                                     UUID ruleId,
                                     Set<String> allowedReconViews) {
        AlertRule rule = alertRuleRepository.findById(ruleId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .orElseThrow(() -> new EntityNotFoundException("Alert rule not found"));
        Map<String, Object> beforeState = snapshotRule(rule);
        alertRuleRepository.delete(rule);
        recordAlertAudit(rule.getTenantId(),
                rule.getReconView(),
                "ALERT_RULE",
                rule.getId(),
                "RULE_DELETED",
                "Alert rule deleted",
                "%s | %s".formatted(rule.getRuleName(), rule.getMetricKey()),
                username,
                "DELETED",
                null,
                beforeState,
                null,
                List.of("ALERT", "RULE"));
        return getAlerts(tenantId, userId, username, null, allowedReconViews);
    }

    @Transactional
    public AlertsResponse saveSubscription(String tenantId,
                                           String userId,
                                           UUID subscriptionId,
                                           SaveAlertEmailSubscriptionRequest request,
                                           String username,
                                           Set<String> allowedReconViews) {
        validateSubscriptionRequest(request);
        String reconView = request.getReconView().toUpperCase();
        if (!allowedReconViews.contains(reconView)) {
            throw new IllegalArgumentException("Not allowed to manage notifications for recon view: " + reconView);
        }

        AlertEmailSubscription subscription = subscriptionId == null
                ? AlertEmailSubscription.builder().tenantId(tenantId).createdBy(username).build()
                : alertEmailSubscriptionRepository.findById(subscriptionId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .orElseThrow(() -> new EntityNotFoundException("Alert email subscription not found"));
        Map<String, Object> beforeState = snapshotEmailSubscription(subscriptionId == null ? null : subscription);

        subscription.setTenantId(tenantId);
        subscription.setSubscriptionName(request.getSubscriptionName().trim());
        subscription.setReconView(reconView);
        subscription.setMetricKey(trimToNull(upperOrNull(request.getMetricKey())));
        subscription.setSeverityThreshold(trimToNull(upperOrNull(request.getSeverityThreshold())));
        subscription.setRecipientType(request.getRecipientType().trim().toUpperCase());
        subscription.setRecipientKey(request.getRecipientKey().trim());
        subscription.setStoreId(trimToNull(request.getStoreId()));
        subscription.setWkstnId(trimToNull(request.getWkstnId()));
        subscription.setActive(request.getActive() == null || request.getActive());
        subscription.setDescription(trimToNull(request.getDescription()));
        if (subscription.getCreatedBy() == null || subscription.getCreatedBy().isBlank()) {
            subscription.setCreatedBy(username);
        }
        subscription.setUpdatedBy(username);

        AlertEmailSubscription savedSubscription = alertEmailSubscriptionRepository.save(subscription);
        recordAlertAudit(savedSubscription.getTenantId(),
                savedSubscription.getReconView(),
                "ALERT_EMAIL_SUBSCRIPTION",
                savedSubscription.getId(),
                subscriptionId == null ? "EMAIL_SUBSCRIPTION_CREATED" : "EMAIL_SUBSCRIPTION_UPDATED",
                subscriptionId == null ? "Alert email subscription created" : "Alert email subscription updated",
                "%s | %s".formatted(savedSubscription.getSubscriptionName(), savedSubscription.getRecipientType()),
                username,
                savedSubscription.isActive() ? "ACTIVE" : "INACTIVE",
                null,
                beforeState,
                snapshotEmailSubscription(savedSubscription),
                List.of("ALERT", "EMAIL"));
        return getAlerts(tenantId, userId, username, null, allowedReconViews);
    }

    @Transactional
    public AlertsResponse deleteSubscription(String tenantId,
                                             String userId,
                                             String username,
                                             UUID subscriptionId,
                                             Set<String> allowedReconViews) {
        AlertEmailSubscription subscription = alertEmailSubscriptionRepository.findById(subscriptionId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .orElseThrow(() -> new EntityNotFoundException("Alert email subscription not found"));
        Map<String, Object> beforeState = snapshotEmailSubscription(subscription);
        alertEmailSubscriptionRepository.delete(subscription);
        recordAlertAudit(subscription.getTenantId(),
                subscription.getReconView(),
                "ALERT_EMAIL_SUBSCRIPTION",
                subscription.getId(),
                "EMAIL_SUBSCRIPTION_DELETED",
                "Alert email subscription deleted",
                "%s | %s".formatted(subscription.getSubscriptionName(), subscription.getRecipientType()),
                username,
                "DELETED",
                null,
                beforeState,
                null,
                List.of("ALERT", "EMAIL"));
        return getAlerts(tenantId, userId, username, null, allowedReconViews);
    }

    @Transactional
    public AlertsResponse saveWebhookSubscription(String tenantId,
                                                  String userId,
                                                  UUID subscriptionId,
                                                  SaveAlertWebhookSubscriptionRequest request,
                                                  String username,
                                                  Set<String> allowedReconViews) {
        validateWebhookSubscriptionRequest(request);
        String reconView = request.getReconView().toUpperCase();
        if (!allowedReconViews.contains(reconView)) {
            throw new IllegalArgumentException("Not allowed to manage notifications for recon view: " + reconView);
        }

        AlertWebhookSubscription subscription = subscriptionId == null
                ? AlertWebhookSubscription.builder().tenantId(tenantId).createdBy(username).build()
                : alertWebhookSubscriptionRepository.findById(subscriptionId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .orElseThrow(() -> new EntityNotFoundException("Alert webhook subscription not found"));
        Map<String, Object> beforeState = snapshotWebhookSubscription(subscriptionId == null ? null : subscription);

        subscription.setTenantId(tenantId);
        subscription.setSubscriptionName(request.getSubscriptionName().trim());
        subscription.setReconView(reconView);
        subscription.setMetricKey(trimToNull(upperOrNull(request.getMetricKey())));
        subscription.setSeverityThreshold(trimToNull(upperOrNull(request.getSeverityThreshold())));
        subscription.setChannelType(request.getChannelType().trim().toUpperCase());
        subscription.setEndpointUrl(request.getEndpointUrl().trim());
        subscription.setStoreId(trimToNull(request.getStoreId()));
        subscription.setWkstnId(trimToNull(request.getWkstnId()));
        subscription.setActive(request.getActive() == null || request.getActive());
        subscription.setDescription(trimToNull(request.getDescription()));
        if (subscription.getCreatedBy() == null || subscription.getCreatedBy().isBlank()) {
            subscription.setCreatedBy(username);
        }
        subscription.setUpdatedBy(username);

        AlertWebhookSubscription savedSubscription = alertWebhookSubscriptionRepository.save(subscription);
        recordAlertAudit(savedSubscription.getTenantId(),
                savedSubscription.getReconView(),
                "ALERT_WEBHOOK_SUBSCRIPTION",
                savedSubscription.getId(),
                subscriptionId == null ? "WEBHOOK_SUBSCRIPTION_CREATED" : "WEBHOOK_SUBSCRIPTION_UPDATED",
                subscriptionId == null ? "Alert webhook subscription created" : "Alert webhook subscription updated",
                "%s | %s".formatted(savedSubscription.getSubscriptionName(), savedSubscription.getChannelType()),
                username,
                savedSubscription.isActive() ? "ACTIVE" : "INACTIVE",
                null,
                beforeState,
                snapshotWebhookSubscription(savedSubscription),
                List.of("ALERT", "WEBHOOK"));
        return getAlerts(tenantId, userId, username, null, allowedReconViews);
    }

    @Transactional
    public AlertsResponse deleteWebhookSubscription(String tenantId,
                                                    String userId,
                                                    String username,
                                                    UUID subscriptionId,
                                                    Set<String> allowedReconViews) {
        AlertWebhookSubscription subscription = alertWebhookSubscriptionRepository.findById(subscriptionId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .orElseThrow(() -> new EntityNotFoundException("Alert webhook subscription not found"));
        Map<String, Object> beforeState = snapshotWebhookSubscription(subscription);
        alertWebhookSubscriptionRepository.delete(subscription);
        recordAlertAudit(subscription.getTenantId(),
                subscription.getReconView(),
                "ALERT_WEBHOOK_SUBSCRIPTION",
                subscription.getId(),
                "WEBHOOK_SUBSCRIPTION_DELETED",
                "Alert webhook subscription deleted",
                "%s | %s".formatted(subscription.getSubscriptionName(), subscription.getChannelType()),
                username,
                "DELETED",
                null,
                beforeState,
                null,
                List.of("ALERT", "WEBHOOK"));
        return getAlerts(tenantId, userId, username, null, allowedReconViews);
    }

    @Transactional
    public AlertsResponse saveEscalationPolicy(String tenantId,
                                               String userId,
                                               String username,
                                               UUID policyId,
                                               SaveAlertEscalationPolicyRequest request,
                                               Set<String> allowedReconViews) {
        validateEscalationPolicyRequest(request);
        String reconView = request.getReconView().toUpperCase(Locale.ROOT);
        if (!allowedReconViews.contains(reconView)) {
            throw new IllegalArgumentException("Not allowed to manage escalation policies for recon view: " + reconView);
        }

        AlertEscalationPolicy policy = policyId == null
                ? AlertEscalationPolicy.builder().tenantId(tenantId).createdBy(username).build()
                : alertEscalationPolicyRepository.findById(policyId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .orElseThrow(() -> new EntityNotFoundException("Alert escalation policy not found"));
        Map<String, Object> beforeState = snapshotEscalationPolicy(policyId == null ? null : policy);

        policy.setTenantId(tenantId);
        policy.setPolicyName(request.getPolicyName().trim());
        policy.setReconView(reconView);
        policy.setMetricKey(trimToNull(upperOrNull(request.getMetricKey())));
        policy.setSeverityThreshold(trimToNull(upperOrNull(request.getSeverityThreshold())));
        policy.setStoreId(trimToNull(request.getStoreId()));
        policy.setWkstnId(trimToNull(request.getWkstnId()));
        policy.setEscalationAfterMinutes(request.getEscalationAfterMinutes() == null || request.getEscalationAfterMinutes() < 0 ? 60 : request.getEscalationAfterMinutes());
        policy.setDestinationType(request.getDestinationType().trim().toUpperCase(Locale.ROOT));
        policy.setDestinationKey(request.getDestinationKey().trim());
        policy.setActive(request.getActive() == null || request.getActive());
        policy.setDescription(trimToNull(request.getDescription()));
        if (policy.getCreatedBy() == null || policy.getCreatedBy().isBlank()) {
            policy.setCreatedBy(username);
        }
        policy.setUpdatedBy(username);

        AlertEscalationPolicy savedPolicy = alertEscalationPolicyRepository.save(policy);
        recordAlertAudit(savedPolicy.getTenantId(),
                savedPolicy.getReconView(),
                "ALERT_ESCALATION_POLICY",
                savedPolicy.getId(),
                policyId == null ? "ESCALATION_POLICY_CREATED" : "ESCALATION_POLICY_UPDATED",
                policyId == null ? "Alert escalation policy created" : "Alert escalation policy updated",
                "%s | %s".formatted(savedPolicy.getPolicyName(), savedPolicy.getDestinationType()),
                username,
                savedPolicy.isActive() ? "ACTIVE" : "INACTIVE",
                null,
                beforeState,
                snapshotEscalationPolicy(savedPolicy),
                List.of("ALERT", "ESCALATION"));
        return getAlerts(tenantId, userId, username, null, allowedReconViews);
    }

    @Transactional
    public AlertsResponse deleteEscalationPolicy(String tenantId,
                                                 String userId,
                                                 String username,
                                                 UUID policyId,
                                                 Set<String> allowedReconViews) {
        AlertEscalationPolicy policy = alertEscalationPolicyRepository.findById(policyId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .orElseThrow(() -> new EntityNotFoundException("Alert escalation policy not found"));
        Map<String, Object> beforeState = snapshotEscalationPolicy(policy);
        alertEscalationPolicyRepository.delete(policy);
        recordAlertAudit(policy.getTenantId(),
                policy.getReconView(),
                "ALERT_ESCALATION_POLICY",
                policy.getId(),
                "ESCALATION_POLICY_DELETED",
                "Alert escalation policy deleted",
                "%s | %s".formatted(policy.getPolicyName(), policy.getDestinationType()),
                username,
                "DELETED",
                null,
                beforeState,
                null,
                List.of("ALERT", "ESCALATION"));
        return getAlerts(tenantId, userId, username, null, allowedReconViews);
    }

    @Transactional
    public AlertsResponse savePersonalSubscription(String tenantId,
                                                   String userId,
                                                   String username,
                                                   UUID subscriptionId,
                                                   SaveAlertUserSubscriptionRequest request,
                                                   Set<String> allowedReconViews) {
        validatePersonalSubscriptionRequest(request);
        String reconView = request.getReconView().toUpperCase(Locale.ROOT);
        if (!allowedReconViews.contains(reconView)) {
            throw new IllegalArgumentException("Not allowed to manage personal subscriptions for recon view: " + reconView);
        }
        UUID principalUserId = UUID.fromString(userId);
        User user = userRepository.findById(principalUserId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        AlertUserSubscription subscription = subscriptionId == null
                ? AlertUserSubscription.builder().tenantId(tenantId).userId(principalUserId).username(username).build()
                : alertUserSubscriptionRepository.findById(subscriptionId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .filter(existing -> principalUserId.equals(existing.getUserId()))
                .orElseThrow(() -> new EntityNotFoundException("Personal alert subscription not found"));
        Map<String, Object> beforeState = snapshotPersonalSubscription(subscriptionId == null ? null : subscription);

        subscription.setTenantId(tenantId);
        subscription.setUserId(user.getId());
        subscription.setUsername(user.getUsername());
        subscription.setReconView(reconView);
        subscription.setMetricKey(trimToNull(upperOrNull(request.getMetricKey())));
        subscription.setSeverityThreshold(trimToNull(upperOrNull(request.getSeverityThreshold())));
        subscription.setChannelType(request.getChannelType().trim().toUpperCase(Locale.ROOT));
        subscription.setEndpointUrl(trimToNull(request.getEndpointUrl()));
        subscription.setStoreId(trimToNull(request.getStoreId()));
        subscription.setWkstnId(trimToNull(request.getWkstnId()));
        subscription.setActive(request.getActive() == null || request.getActive());
        subscription.setDescription(trimToNull(request.getDescription()));

        AlertUserSubscription savedSubscription = alertUserSubscriptionRepository.save(subscription);
        recordAlertAudit(savedSubscription.getTenantId(),
                savedSubscription.getReconView(),
                "ALERT_PERSONAL_SUBSCRIPTION",
                savedSubscription.getId(),
                subscriptionId == null ? "PERSONAL_SUBSCRIPTION_CREATED" : "PERSONAL_SUBSCRIPTION_UPDATED",
                subscriptionId == null ? "Personal alert subscription created" : "Personal alert subscription updated",
                "%s | %s".formatted(savedSubscription.getUsername(), savedSubscription.getChannelType()),
                username,
                savedSubscription.isActive() ? "ACTIVE" : "INACTIVE",
                null,
                beforeState,
                snapshotPersonalSubscription(savedSubscription),
                List.of("ALERT", "PERSONAL_SUBSCRIPTION"));
        return getAlerts(tenantId, userId, username, null, allowedReconViews);
    }

    @Transactional
    public AlertsResponse deletePersonalSubscription(String tenantId,
                                                     String userId,
                                                     String username,
                                                     UUID subscriptionId,
                                                     Set<String> allowedReconViews) {
        UUID principalUserId = UUID.fromString(userId);
        AlertUserSubscription subscription = alertUserSubscriptionRepository.findById(subscriptionId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .filter(existing -> principalUserId.equals(existing.getUserId()))
                .orElseThrow(() -> new EntityNotFoundException("Personal alert subscription not found"));
        Map<String, Object> beforeState = snapshotPersonalSubscription(subscription);
        alertUserSubscriptionRepository.delete(subscription);
        recordAlertAudit(subscription.getTenantId(),
                subscription.getReconView(),
                "ALERT_PERSONAL_SUBSCRIPTION",
                subscription.getId(),
                "PERSONAL_SUBSCRIPTION_DELETED",
                "Personal alert subscription deleted",
                "%s | %s".formatted(subscription.getUsername(), subscription.getChannelType()),
                username,
                "DELETED",
                null,
                beforeState,
                null,
                List.of("ALERT", "PERSONAL_SUBSCRIPTION"));
        return getAlerts(tenantId, userId, username, null, allowedReconViews);
    }

    @Transactional
    public AlertsResponse saveDigestSubscription(String tenantId,
                                                 String userId,
                                                 UUID subscriptionId,
                                                 SaveAlertDigestSubscriptionRequest request,
                                                 String username,
                                                 Set<String> allowedReconViews) {
        validateDigestSubscriptionRequest(request);
        String reconView = request.getReconView().toUpperCase(Locale.ROOT);
        if (!allowedReconViews.contains(reconView)) {
            throw new IllegalArgumentException("Not allowed to manage digests for recon view: " + reconView);
        }

        AlertDigestSubscription subscription = subscriptionId == null
                ? AlertDigestSubscription.builder().tenantId(tenantId).createdBy(username).build()
                : alertDigestSubscriptionRepository.findById(subscriptionId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .orElseThrow(() -> new EntityNotFoundException("Alert digest subscription not found"));
        Map<String, Object> beforeState = snapshotDigestSubscription(subscriptionId == null ? null : subscription);

        subscription.setTenantId(tenantId);
        subscription.setDigestName(request.getDigestName().trim());
        subscription.setReconView(reconView);
        subscription.setScopeType(request.getScopeType().trim().toUpperCase(Locale.ROOT));
        subscription.setScopeKey(trimToNull(request.getScopeKey()));
        subscription.setSeverityThreshold(trimToNull(upperOrNull(request.getSeverityThreshold())));
        subscription.setRecipientType(request.getRecipientType().trim().toUpperCase(Locale.ROOT));
        subscription.setRecipientKey(request.getRecipientKey().trim());
        subscription.setActive(request.getActive() == null || request.getActive());
        subscription.setDescription(trimToNull(request.getDescription()));
        if (subscription.getCreatedBy() == null || subscription.getCreatedBy().isBlank()) {
            subscription.setCreatedBy(username);
        }
        subscription.setUpdatedBy(username);

        AlertDigestSubscription savedSubscription = alertDigestSubscriptionRepository.save(subscription);
        recordAlertAudit(savedSubscription.getTenantId(),
                savedSubscription.getReconView(),
                "ALERT_DIGEST_SUBSCRIPTION",
                savedSubscription.getId(),
                subscriptionId == null ? "DIGEST_SUBSCRIPTION_CREATED" : "DIGEST_SUBSCRIPTION_UPDATED",
                subscriptionId == null ? "Alert daily digest created" : "Alert daily digest updated",
                "%s | %s".formatted(savedSubscription.getDigestName(), savedSubscription.getScopeType()),
                username,
                savedSubscription.isActive() ? "ACTIVE" : "INACTIVE",
                null,
                beforeState,
                snapshotDigestSubscription(savedSubscription),
                List.of("ALERT", "DIGEST"));
        return getAlerts(tenantId, userId, username, null, allowedReconViews);
    }

    @Transactional
    public AlertsResponse deleteDigestSubscription(String tenantId,
                                                   String userId,
                                                   String username,
                                                   UUID subscriptionId,
                                                   Set<String> allowedReconViews) {
        AlertDigestSubscription subscription = alertDigestSubscriptionRepository.findById(subscriptionId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .orElseThrow(() -> new EntityNotFoundException("Alert digest subscription not found"));
        Map<String, Object> beforeState = snapshotDigestSubscription(subscription);
        alertDigestSubscriptionRepository.delete(subscription);
        recordAlertAudit(subscription.getTenantId(),
                subscription.getReconView(),
                "ALERT_DIGEST_SUBSCRIPTION",
                subscription.getId(),
                "DIGEST_SUBSCRIPTION_DELETED",
                "Alert daily digest deleted",
                "%s | %s".formatted(subscription.getDigestName(), subscription.getScopeType()),
                username,
                "DELETED",
                null,
                beforeState,
                null,
                List.of("ALERT", "DIGEST"));
        return getAlerts(tenantId, userId, username, null, allowedReconViews);
    }

    @Transactional
    public AlertsResponse saveAnomalyRule(String tenantId,
                                          String userId,
                                          UUID ruleId,
                                          SaveAlertAnomalyRuleRequest request,
                                          String username,
                                          Set<String> allowedReconViews) {
        validateAnomalyRuleRequest(request);
        String reconView = request.getReconView().toUpperCase(Locale.ROOT);
        if (!allowedReconViews.contains(reconView)) {
            throw new IllegalArgumentException("Not allowed to manage anomaly rules for recon view: " + reconView);
        }

        AlertAnomalyRule rule = ruleId == null
                ? AlertAnomalyRule.builder().tenantId(tenantId).createdBy(username).build()
                : alertAnomalyRuleRepository.findById(ruleId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .orElseThrow(() -> new EntityNotFoundException("Alert anomaly rule not found"));
        Map<String, Object> beforeState = snapshotAnomalyRule(ruleId == null ? null : rule);

        rule.setTenantId(tenantId);
        rule.setRuleName(request.getRuleName().trim());
        rule.setReconView(reconView);
        rule.setMetricKey(request.getMetricKey().trim().toUpperCase(Locale.ROOT));
        rule.setAnomalyType(request.getAnomalyType().trim().toUpperCase(Locale.ROOT));
        rule.setPercentChangeThreshold(request.getPercentChangeThreshold());
        rule.setMinBaselineValue(request.getMinBaselineValue());
        rule.setLookbackDays(request.getLookbackDays() == null || request.getLookbackDays() <= 0 ? 7 : request.getLookbackDays());
        rule.setCooldownMinutes(request.getCooldownMinutes() == null || request.getCooldownMinutes() < 0 ? 180 : request.getCooldownMinutes());
        rule.setSeverity(request.getSeverity().trim().toUpperCase(Locale.ROOT));
        rule.setStoreId(trimToNull(request.getStoreId()));
        rule.setActive(request.getActive() == null || request.getActive());
        rule.setDescription(trimToNull(request.getDescription()));
        if (rule.getCreatedBy() == null || rule.getCreatedBy().isBlank()) {
            rule.setCreatedBy(username);
        }
        rule.setUpdatedBy(username);

        AlertAnomalyRule savedRule = alertAnomalyRuleRepository.save(rule);
        recordAlertAudit(savedRule.getTenantId(),
                savedRule.getReconView(),
                "ALERT_ANOMALY_RULE",
                savedRule.getId(),
                ruleId == null ? "ANOMALY_RULE_CREATED" : "ANOMALY_RULE_UPDATED",
                ruleId == null ? "Alert anomaly rule created" : "Alert anomaly rule updated",
                "%s | %s".formatted(savedRule.getRuleName(), savedRule.getAnomalyType()),
                username,
                savedRule.isActive() ? "ACTIVE" : "INACTIVE",
                null,
                beforeState,
                snapshotAnomalyRule(savedRule),
                List.of("ALERT", "ANOMALY"));
        return getAlerts(tenantId, userId, username, null, allowedReconViews);
    }

    @Transactional
    public AlertsResponse deleteAnomalyRule(String tenantId,
                                            String userId,
                                            String username,
                                            UUID ruleId,
                                            Set<String> allowedReconViews) {
        AlertAnomalyRule rule = alertAnomalyRuleRepository.findById(ruleId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .orElseThrow(() -> new EntityNotFoundException("Alert anomaly rule not found"));
        Map<String, Object> beforeState = snapshotAnomalyRule(rule);
        alertAnomalyRuleRepository.delete(rule);
        recordAlertAudit(rule.getTenantId(),
                rule.getReconView(),
                "ALERT_ANOMALY_RULE",
                rule.getId(),
                "ANOMALY_RULE_DELETED",
                "Alert anomaly rule deleted",
                "%s | %s".formatted(rule.getRuleName(), rule.getAnomalyType()),
                username,
                "DELETED",
                null,
                beforeState,
                null,
                List.of("ALERT", "ANOMALY"));
        return getAlerts(tenantId, userId, username, null, allowedReconViews);
    }

    @Transactional
    public AlertsResponse saveSmsSubscription(String tenantId,
                                              String userId,
                                              UUID subscriptionId,
                                              SaveAlertSmsSubscriptionRequest request,
                                              String username,
                                              Set<String> allowedReconViews) {
        validateSmsSubscriptionRequest(request);
        String reconView = request.getReconView().toUpperCase(Locale.ROOT);
        if (!allowedReconViews.contains(reconView)) {
            throw new IllegalArgumentException("Not allowed to manage SMS subscriptions for recon view: " + reconView);
        }

        AlertSmsSubscription subscription = subscriptionId == null
                ? AlertSmsSubscription.builder().tenantId(tenantId).createdBy(username).build()
                : alertSmsSubscriptionRepository.findById(subscriptionId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .orElseThrow(() -> new EntityNotFoundException("Alert SMS subscription not found"));
        Map<String, Object> beforeState = snapshotSmsSubscription(subscriptionId == null ? null : subscription);

        subscription.setTenantId(tenantId);
        subscription.setSubscriptionName(request.getSubscriptionName().trim());
        subscription.setReconView(reconView);
        subscription.setMetricKey(trimToNull(upperOrNull(request.getMetricKey())));
        subscription.setSeverityThreshold(trimToNull(upperOrNull(request.getSeverityThreshold())));
        subscription.setPhoneNumber(request.getPhoneNumber().trim());
        subscription.setStoreId(trimToNull(request.getStoreId()));
        subscription.setWkstnId(trimToNull(request.getWkstnId()));
        subscription.setActive(request.getActive() == null || request.getActive());
        subscription.setDescription(trimToNull(request.getDescription()));
        if (subscription.getCreatedBy() == null || subscription.getCreatedBy().isBlank()) {
            subscription.setCreatedBy(username);
        }
        subscription.setUpdatedBy(username);

        AlertSmsSubscription savedSubscription = alertSmsSubscriptionRepository.save(subscription);
        recordAlertAudit(savedSubscription.getTenantId(),
                savedSubscription.getReconView(),
                "ALERT_SMS_SUBSCRIPTION",
                savedSubscription.getId(),
                subscriptionId == null ? "SMS_SUBSCRIPTION_CREATED" : "SMS_SUBSCRIPTION_UPDATED",
                subscriptionId == null ? "Alert SMS subscription created" : "Alert SMS subscription updated",
                "%s | %s".formatted(savedSubscription.getSubscriptionName(), savedSubscription.getPhoneNumber()),
                username,
                savedSubscription.isActive() ? "ACTIVE" : "INACTIVE",
                null,
                beforeState,
                snapshotSmsSubscription(savedSubscription),
                List.of("ALERT", "SMS"));
        return getAlerts(tenantId, userId, username, null, allowedReconViews);
    }

    @Transactional
    public AlertsResponse deleteSmsSubscription(String tenantId,
                                                String userId,
                                                String username,
                                                UUID subscriptionId,
                                                Set<String> allowedReconViews) {
        AlertSmsSubscription subscription = alertSmsSubscriptionRepository.findById(subscriptionId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .orElseThrow(() -> new EntityNotFoundException("Alert SMS subscription not found"));
        Map<String, Object> beforeState = snapshotSmsSubscription(subscription);
        alertSmsSubscriptionRepository.delete(subscription);
        recordAlertAudit(subscription.getTenantId(),
                subscription.getReconView(),
                "ALERT_SMS_SUBSCRIPTION",
                subscription.getId(),
                "SMS_SUBSCRIPTION_DELETED",
                "Alert SMS subscription deleted",
                "%s | %s".formatted(subscription.getSubscriptionName(), subscription.getPhoneNumber()),
                username,
                "DELETED",
                null,
                beforeState,
                null,
                List.of("ALERT", "SMS"));
        return getAlerts(tenantId, userId, username, null, allowedReconViews);
    }

    @Transactional
    public AlertsResponse updateEventStatus(String tenantId,
                                            String userId,
                                            String username,
                                            UUID eventId,
                                            UpdateAlertEventRequest request,
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
        Map<String, Object> beforeState = snapshotEvent(event);

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
        AlertEvent savedEvent = alertEventRepository.save(event);
        recordAlertAudit(savedEvent.getTenantId(),
                savedEvent.getReconView(),
                "ALERT_EVENT",
                savedEvent.getId(),
                "ACKNOWLEDGED".equals(status) ? "EVENT_ACKNOWLEDGED" : "EVENT_RESOLVED",
                "ACKNOWLEDGED".equals(status) ? "Alert event acknowledged" : "Alert event resolved",
                savedEvent.getEventMessage(),
                username,
                savedEvent.getAlertStatus(),
                null,
                beforeState,
                snapshotEvent(savedEvent),
                List.of("ALERT", "EVENT"));
        return getAlerts(tenantId, userId, username, null, allowedReconViews);
    }

    private void recordAlertAudit(String tenantId,
                                  String moduleKey,
                                  String entityType,
                                  UUID entityId,
                                  String actionType,
                                  String title,
                                  String summary,
                                  String actor,
                                  String status,
                                  String reason,
                                  Object beforeState,
                                  Object afterState,
                                  List<String> evidenceTags) {
        if (tenantId == null || tenantId.isBlank()) {
            return;
        }
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("ALERT")
                .moduleKey(moduleKey == null || moduleKey.isBlank() ? "ALERTS" : moduleKey)
                .entityType(entityType)
                .entityKey(entityId != null ? entityId.toString() : defaultIfBlank(summary, title))
                .actionType(actionType)
                .title(title)
                .summary(trimToNull(summary))
                .actor(trimToNull(actor))
                .reason(trimToNull(reason))
                .status(trimToNull(status))
                .referenceKey(entityId != null ? entityId.toString() : null)
                .controlFamily("MONITORING")
                .evidenceTags(evidenceTags)
                .beforeState(beforeState)
                .afterState(afterState)
                .eventAt(LocalDateTime.now())
                .build());
    }

    private Map<String, Object> snapshotRule(AlertRule rule) {
        if (rule == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", rule.getId());
        snapshot.put("ruleName", rule.getRuleName());
        snapshot.put("reconView", rule.getReconView());
        snapshot.put("metricKey", rule.getMetricKey());
        snapshot.put("operator", rule.getOperator());
        snapshot.put("thresholdValue", rule.getThresholdValue());
        snapshot.put("severity", rule.getSeverity());
        snapshot.put("storeId", trimToNull(rule.getStoreId()));
        snapshot.put("wkstnId", trimToNull(rule.getWkstnId()));
        snapshot.put("lookbackDays", rule.getLookbackDays());
        snapshot.put("cooldownMinutes", rule.getCooldownMinutes());
        snapshot.put("active", rule.isActive());
        snapshot.put("description", trimToNull(rule.getDescription()));
        snapshot.put("createdBy", trimToNull(rule.getCreatedBy()));
        snapshot.put("updatedBy", trimToNull(rule.getUpdatedBy()));
        snapshot.put("createdAt", valueOrNull(rule.getCreatedAt()));
        snapshot.put("updatedAt", valueOrNull(rule.getUpdatedAt()));
        return snapshot;
    }

    private Map<String, Object> snapshotEmailSubscription(AlertEmailSubscription subscription) {
        if (subscription == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", subscription.getId());
        snapshot.put("subscriptionName", subscription.getSubscriptionName());
        snapshot.put("reconView", subscription.getReconView());
        snapshot.put("metricKey", trimToNull(subscription.getMetricKey()));
        snapshot.put("severityThreshold", trimToNull(subscription.getSeverityThreshold()));
        snapshot.put("recipientType", subscription.getRecipientType());
        snapshot.put("recipientKey", trimToNull(subscription.getRecipientKey()));
        snapshot.put("storeId", trimToNull(subscription.getStoreId()));
        snapshot.put("wkstnId", trimToNull(subscription.getWkstnId()));
        snapshot.put("active", subscription.isActive());
        snapshot.put("description", trimToNull(subscription.getDescription()));
        snapshot.put("createdBy", trimToNull(subscription.getCreatedBy()));
        snapshot.put("updatedBy", trimToNull(subscription.getUpdatedBy()));
        snapshot.put("createdAt", valueOrNull(subscription.getCreatedAt()));
        snapshot.put("updatedAt", valueOrNull(subscription.getUpdatedAt()));
        return snapshot;
    }

    private Map<String, Object> snapshotWebhookSubscription(AlertWebhookSubscription subscription) {
        if (subscription == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", subscription.getId());
        snapshot.put("subscriptionName", subscription.getSubscriptionName());
        snapshot.put("reconView", subscription.getReconView());
        snapshot.put("metricKey", trimToNull(subscription.getMetricKey()));
        snapshot.put("severityThreshold", trimToNull(subscription.getSeverityThreshold()));
        snapshot.put("channelType", subscription.getChannelType());
        snapshot.put("endpointUrl", trimToNull(subscription.getEndpointUrl()));
        snapshot.put("storeId", trimToNull(subscription.getStoreId()));
        snapshot.put("wkstnId", trimToNull(subscription.getWkstnId()));
        snapshot.put("active", subscription.isActive());
        snapshot.put("description", trimToNull(subscription.getDescription()));
        snapshot.put("createdBy", trimToNull(subscription.getCreatedBy()));
        snapshot.put("updatedBy", trimToNull(subscription.getUpdatedBy()));
        snapshot.put("createdAt", valueOrNull(subscription.getCreatedAt()));
        snapshot.put("updatedAt", valueOrNull(subscription.getUpdatedAt()));
        return snapshot;
    }

    private Map<String, Object> snapshotEscalationPolicy(AlertEscalationPolicy policy) {
        if (policy == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", policy.getId());
        snapshot.put("policyName", policy.getPolicyName());
        snapshot.put("reconView", policy.getReconView());
        snapshot.put("metricKey", trimToNull(policy.getMetricKey()));
        snapshot.put("severityThreshold", trimToNull(policy.getSeverityThreshold()));
        snapshot.put("storeId", trimToNull(policy.getStoreId()));
        snapshot.put("wkstnId", trimToNull(policy.getWkstnId()));
        snapshot.put("escalationAfterMinutes", policy.getEscalationAfterMinutes());
        snapshot.put("destinationType", policy.getDestinationType());
        snapshot.put("destinationKey", trimToNull(policy.getDestinationKey()));
        snapshot.put("active", policy.isActive());
        snapshot.put("description", trimToNull(policy.getDescription()));
        snapshot.put("createdBy", trimToNull(policy.getCreatedBy()));
        snapshot.put("updatedBy", trimToNull(policy.getUpdatedBy()));
        snapshot.put("createdAt", valueOrNull(policy.getCreatedAt()));
        snapshot.put("updatedAt", valueOrNull(policy.getUpdatedAt()));
        return snapshot;
    }

    private Map<String, Object> snapshotPersonalSubscription(AlertUserSubscription subscription) {
        if (subscription == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", subscription.getId());
        snapshot.put("userId", subscription.getUserId());
        snapshot.put("username", subscription.getUsername());
        snapshot.put("reconView", subscription.getReconView());
        snapshot.put("metricKey", trimToNull(subscription.getMetricKey()));
        snapshot.put("severityThreshold", trimToNull(subscription.getSeverityThreshold()));
        snapshot.put("channelType", subscription.getChannelType());
        snapshot.put("endpointUrl", trimToNull(subscription.getEndpointUrl()));
        snapshot.put("storeId", trimToNull(subscription.getStoreId()));
        snapshot.put("wkstnId", trimToNull(subscription.getWkstnId()));
        snapshot.put("active", subscription.isActive());
        snapshot.put("description", trimToNull(subscription.getDescription()));
        snapshot.put("createdAt", valueOrNull(subscription.getCreatedAt()));
        snapshot.put("updatedAt", valueOrNull(subscription.getUpdatedAt()));
        return snapshot;
    }

    private Map<String, Object> snapshotDigestSubscription(AlertDigestSubscription subscription) {
        if (subscription == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", subscription.getId());
        snapshot.put("digestName", subscription.getDigestName());
        snapshot.put("reconView", subscription.getReconView());
        snapshot.put("scopeType", subscription.getScopeType());
        snapshot.put("scopeKey", trimToNull(subscription.getScopeKey()));
        snapshot.put("severityThreshold", trimToNull(subscription.getSeverityThreshold()));
        snapshot.put("recipientType", subscription.getRecipientType());
        snapshot.put("recipientKey", trimToNull(subscription.getRecipientKey()));
        snapshot.put("active", subscription.isActive());
        snapshot.put("description", trimToNull(subscription.getDescription()));
        snapshot.put("createdBy", trimToNull(subscription.getCreatedBy()));
        snapshot.put("updatedBy", trimToNull(subscription.getUpdatedBy()));
        snapshot.put("createdAt", valueOrNull(subscription.getCreatedAt()));
        snapshot.put("updatedAt", valueOrNull(subscription.getUpdatedAt()));
        return snapshot;
    }

    private Map<String, Object> snapshotAnomalyRule(AlertAnomalyRule rule) {
        if (rule == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", rule.getId());
        snapshot.put("ruleName", rule.getRuleName());
        snapshot.put("reconView", rule.getReconView());
        snapshot.put("metricKey", rule.getMetricKey());
        snapshot.put("anomalyType", rule.getAnomalyType());
        snapshot.put("percentChangeThreshold", rule.getPercentChangeThreshold());
        snapshot.put("minBaselineValue", rule.getMinBaselineValue());
        snapshot.put("lookbackDays", rule.getLookbackDays());
        snapshot.put("cooldownMinutes", rule.getCooldownMinutes());
        snapshot.put("severity", rule.getSeverity());
        snapshot.put("storeId", trimToNull(rule.getStoreId()));
        snapshot.put("active", rule.isActive());
        snapshot.put("description", trimToNull(rule.getDescription()));
        snapshot.put("createdBy", trimToNull(rule.getCreatedBy()));
        snapshot.put("updatedBy", trimToNull(rule.getUpdatedBy()));
        snapshot.put("createdAt", valueOrNull(rule.getCreatedAt()));
        snapshot.put("updatedAt", valueOrNull(rule.getUpdatedAt()));
        return snapshot;
    }

    private Map<String, Object> snapshotSmsSubscription(AlertSmsSubscription subscription) {
        if (subscription == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", subscription.getId());
        snapshot.put("subscriptionName", subscription.getSubscriptionName());
        snapshot.put("reconView", subscription.getReconView());
        snapshot.put("metricKey", trimToNull(subscription.getMetricKey()));
        snapshot.put("severityThreshold", trimToNull(subscription.getSeverityThreshold()));
        snapshot.put("phoneNumber", trimToNull(subscription.getPhoneNumber()));
        snapshot.put("storeId", trimToNull(subscription.getStoreId()));
        snapshot.put("wkstnId", trimToNull(subscription.getWkstnId()));
        snapshot.put("active", subscription.isActive());
        snapshot.put("description", trimToNull(subscription.getDescription()));
        snapshot.put("createdBy", trimToNull(subscription.getCreatedBy()));
        snapshot.put("updatedBy", trimToNull(subscription.getUpdatedBy()));
        snapshot.put("createdAt", valueOrNull(subscription.getCreatedAt()));
        snapshot.put("updatedAt", valueOrNull(subscription.getUpdatedAt()));
        return snapshot;
    }

    private Map<String, Object> snapshotEvent(AlertEvent event) {
        if (event == null) {
            return null;
        }
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
        snapshot.put("wkstnId", trimToNull(event.getWkstnId()));
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
        snapshot.put("createdAt", valueOrNull(event.getCreatedAt()));
        snapshot.put("updatedAt", valueOrNull(event.getUpdatedAt()));
        return snapshot;
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String valueOrNull(Object value) {
        return value == null ? null : Objects.toString(value, null);
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

    private void validateSubscriptionRequest(SaveAlertEmailSubscriptionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Alert email subscription request is required");
        }
        if (request.getSubscriptionName() == null || request.getSubscriptionName().isBlank()) {
            throw new IllegalArgumentException("Subscription name is required");
        }
        if (request.getReconView() == null || !VALID_RECON_VIEWS.contains(request.getReconView().toUpperCase())) {
            throw new IllegalArgumentException("Valid reconView is required");
        }
        if (request.getMetricKey() != null && !request.getMetricKey().isBlank()
                && !VALID_METRICS.contains(request.getMetricKey().toUpperCase())) {
            throw new IllegalArgumentException("Valid metricKey is required when specified");
        }
        if (request.getSeverityThreshold() != null && !request.getSeverityThreshold().isBlank()
                && !VALID_SEVERITIES.contains(request.getSeverityThreshold().toUpperCase())) {
            throw new IllegalArgumentException("Valid severityThreshold is required when specified");
        }
        if (request.getRecipientType() == null || !VALID_RECIPIENT_TYPES.contains(request.getRecipientType().toUpperCase())) {
            throw new IllegalArgumentException("Valid recipientType is required");
        }
        if (request.getRecipientKey() == null || request.getRecipientKey().isBlank()) {
            throw new IllegalArgumentException("Recipient value is required");
        }
        if ("EMAIL".equalsIgnoreCase(request.getRecipientType()) && !request.getRecipientKey().contains("@")) {
            throw new IllegalArgumentException("Valid recipient email is required");
        }
    }

    private void validateWebhookSubscriptionRequest(SaveAlertWebhookSubscriptionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Alert webhook subscription request is required");
        }
        if (request.getSubscriptionName() == null || request.getSubscriptionName().isBlank()) {
            throw new IllegalArgumentException("Subscription name is required");
        }
        if (request.getReconView() == null || !VALID_RECON_VIEWS.contains(request.getReconView().toUpperCase())) {
            throw new IllegalArgumentException("Valid reconView is required");
        }
        if (request.getMetricKey() != null && !request.getMetricKey().isBlank()
                && !VALID_METRICS.contains(request.getMetricKey().toUpperCase())) {
            throw new IllegalArgumentException("Valid metricKey is required when specified");
        }
        if (request.getSeverityThreshold() != null && !request.getSeverityThreshold().isBlank()
                && !VALID_SEVERITIES.contains(request.getSeverityThreshold().toUpperCase())) {
            throw new IllegalArgumentException("Valid severityThreshold is required when specified");
        }
        if (request.getChannelType() == null || !VALID_WEBHOOK_CHANNEL_TYPES.contains(request.getChannelType().toUpperCase())) {
            throw new IllegalArgumentException("Valid channelType is required");
        }
        if (request.getEndpointUrl() == null || request.getEndpointUrl().isBlank()) {
            throw new IllegalArgumentException("Endpoint URL is required");
        }
        String endpointUrl = request.getEndpointUrl().trim().toLowerCase();
        if (!endpointUrl.startsWith("http://") && !endpointUrl.startsWith("https://")) {
            throw new IllegalArgumentException("Endpoint URL must start with http:// or https://");
        }
    }

    private void validateEscalationPolicyRequest(SaveAlertEscalationPolicyRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Alert escalation policy request is required");
        }
        if (request.getPolicyName() == null || request.getPolicyName().isBlank()) {
            throw new IllegalArgumentException("Policy name is required");
        }
        if (request.getReconView() == null || !VALID_RECON_VIEWS.contains(request.getReconView().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Valid reconView is required");
        }
        if (request.getMetricKey() != null && !request.getMetricKey().isBlank()
                && !VALID_METRICS.contains(request.getMetricKey().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Valid metricKey is required when specified");
        }
        if (request.getSeverityThreshold() != null && !request.getSeverityThreshold().isBlank()
                && !VALID_SEVERITIES.contains(request.getSeverityThreshold().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Valid severityThreshold is required when specified");
        }
        if (request.getDestinationType() == null || !VALID_ESCALATION_DESTINATION_TYPES.contains(request.getDestinationType().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Valid destinationType is required");
        }
        if (request.getDestinationKey() == null || request.getDestinationKey().isBlank()) {
            throw new IllegalArgumentException("Destination value is required");
        }
        if (request.getEscalationAfterMinutes() == null || request.getEscalationAfterMinutes() < 0) {
            throw new IllegalArgumentException("Valid escalationAfterMinutes is required");
        }
    }

    private void validatePersonalSubscriptionRequest(SaveAlertUserSubscriptionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Personal alert subscription request is required");
        }
        if (request.getReconView() == null || !VALID_RECON_VIEWS.contains(request.getReconView().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Valid reconView is required");
        }
        if (request.getMetricKey() != null && !request.getMetricKey().isBlank()
                && !VALID_METRICS.contains(request.getMetricKey().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Valid metricKey is required when specified");
        }
        if (request.getSeverityThreshold() != null && !request.getSeverityThreshold().isBlank()
                && !VALID_SEVERITIES.contains(request.getSeverityThreshold().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Valid severityThreshold is required when specified");
        }
        if (request.getChannelType() == null || !VALID_PERSONAL_CHANNEL_TYPES.contains(request.getChannelType().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Valid channelType is required");
        }
        if (!"EMAIL".equalsIgnoreCase(request.getChannelType())) {
            if (request.getEndpointUrl() == null || request.getEndpointUrl().isBlank()) {
                throw new IllegalArgumentException("Endpoint URL is required for webhook-based personal subscriptions");
            }
            String endpointUrl = request.getEndpointUrl().trim().toLowerCase(Locale.ROOT);
            if (!endpointUrl.startsWith("http://") && !endpointUrl.startsWith("https://")) {
                throw new IllegalArgumentException("Endpoint URL must start with http:// or https://");
            }
        }
    }

    private void validateDigestSubscriptionRequest(SaveAlertDigestSubscriptionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Alert digest request is required");
        }
        if (request.getDigestName() == null || request.getDigestName().isBlank()) {
            throw new IllegalArgumentException("Digest name is required");
        }
        if (request.getReconView() == null || !VALID_RECON_VIEWS.contains(request.getReconView().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Valid reconView is required");
        }
        if (request.getScopeType() == null || !VALID_DIGEST_SCOPE_TYPES.contains(request.getScopeType().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Valid scopeType is required");
        }
        if (request.getSeverityThreshold() != null && !request.getSeverityThreshold().isBlank()
                && !VALID_SEVERITIES.contains(request.getSeverityThreshold().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Valid severityThreshold is required when specified");
        }
        if (request.getRecipientType() == null || !VALID_RECIPIENT_TYPES.contains(request.getRecipientType().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Valid recipientType is required");
        }
        if (request.getRecipientKey() == null || request.getRecipientKey().isBlank()) {
            throw new IllegalArgumentException("Recipient value is required");
        }
        if ("EMAIL".equalsIgnoreCase(request.getRecipientType()) && !request.getRecipientKey().contains("@")) {
            throw new IllegalArgumentException("Valid recipient email is required");
        }
    }

    private void validateAnomalyRuleRequest(SaveAlertAnomalyRuleRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Alert anomaly rule request is required");
        }
        if (request.getRuleName() == null || request.getRuleName().isBlank()) {
            throw new IllegalArgumentException("Rule name is required");
        }
        if (request.getReconView() == null || !VALID_RECON_VIEWS.contains(request.getReconView().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Valid reconView is required");
        }
        if (request.getMetricKey() == null || !VALID_METRICS.contains(request.getMetricKey().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Valid metricKey is required");
        }
        if (request.getAnomalyType() == null || !VALID_ANOMALY_TYPES.contains(request.getAnomalyType().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Valid anomalyType is required");
        }
        if (request.getPercentChangeThreshold() == null || request.getPercentChangeThreshold().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Valid percentChangeThreshold is required");
        }
        if (request.getMinBaselineValue() == null || request.getMinBaselineValue().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Valid minBaselineValue is required");
        }
        if (request.getSeverity() == null || !VALID_SEVERITIES.contains(request.getSeverity().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Valid severity is required");
        }
    }

    private void validateSmsSubscriptionRequest(SaveAlertSmsSubscriptionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Alert SMS subscription request is required");
        }
        if (request.getSubscriptionName() == null || request.getSubscriptionName().isBlank()) {
            throw new IllegalArgumentException("Subscription name is required");
        }
        if (request.getReconView() == null || !VALID_RECON_VIEWS.contains(request.getReconView().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Valid reconView is required");
        }
        if (request.getMetricKey() != null && !request.getMetricKey().isBlank()
                && !VALID_METRICS.contains(request.getMetricKey().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Valid metricKey is required when specified");
        }
        if (request.getSeverityThreshold() != null && !request.getSeverityThreshold().isBlank()
                && !VALID_SEVERITIES.contains(request.getSeverityThreshold().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Valid severityThreshold is required when specified");
        }
        if (request.getPhoneNumber() == null || request.getPhoneNumber().isBlank()) {
            throw new IllegalArgumentException("Phone number is required");
        }
        String normalized = request.getPhoneNumber().trim();
        if (!normalized.matches("[+0-9 ()-]{7,40}")) {
            throw new IllegalArgumentException("Phone number format is invalid");
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
                .anomalyRuleId(event.getAnomalyRuleId())
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
                .detectionType(event.getDetectionType())
                .anomalyDirection(event.getAnomalyDirection())
                .baselineValue(event.getBaselineValue())
                .deltaPercentage(event.getDeltaPercentage())
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

    private AlertEmailSubscriptionDto toDto(AlertEmailSubscription subscription) {
        return AlertEmailSubscriptionDto.builder()
                .id(subscription.getId())
                .subscriptionName(subscription.getSubscriptionName())
                .reconView(subscription.getReconView())
                .metricKey(subscription.getMetricKey())
                .severityThreshold(subscription.getSeverityThreshold())
                .recipientType(subscription.getRecipientType())
                .recipientKey(subscription.getRecipientKey())
                .storeId(subscription.getStoreId())
                .wkstnId(subscription.getWkstnId())
                .active(subscription.isActive())
                .description(subscription.getDescription())
                .createdBy(subscription.getCreatedBy())
                .updatedBy(subscription.getUpdatedBy())
                .createdAt(subscription.getCreatedAt())
                .updatedAt(subscription.getUpdatedAt())
                .build();
    }

    private AlertEmailDeliveryDto toDto(AlertEmailDelivery delivery) {
        return AlertEmailDeliveryDto.builder()
                .id(delivery.getId())
                .eventId(delivery.getEventId())
                .subscriptionId(delivery.getSubscriptionId())
                .reconView(delivery.getReconView())
                .recipientEmail(delivery.getRecipientEmail())
                .deliveryStatus(delivery.getDeliveryStatus())
                .emailSubject(delivery.getEmailSubject())
                .errorMessage(delivery.getErrorMessage())
                .createdAt(delivery.getCreatedAt())
                .lastAttemptAt(delivery.getLastAttemptAt())
                .deliveredAt(delivery.getDeliveredAt())
                .build();
    }

    private AlertWebhookSubscriptionDto toDto(AlertWebhookSubscription subscription) {
        return AlertWebhookSubscriptionDto.builder()
                .id(subscription.getId())
                .subscriptionName(subscription.getSubscriptionName())
                .reconView(subscription.getReconView())
                .metricKey(subscription.getMetricKey())
                .severityThreshold(subscription.getSeverityThreshold())
                .channelType(subscription.getChannelType())
                .endpointUrl(subscription.getEndpointUrl())
                .storeId(subscription.getStoreId())
                .wkstnId(subscription.getWkstnId())
                .active(subscription.isActive())
                .description(subscription.getDescription())
                .createdBy(subscription.getCreatedBy())
                .updatedBy(subscription.getUpdatedBy())
                .createdAt(subscription.getCreatedAt())
                .updatedAt(subscription.getUpdatedAt())
                .build();
    }

    private AlertWebhookDeliveryDto toDto(AlertWebhookDelivery delivery) {
        return AlertWebhookDeliveryDto.builder()
                .id(delivery.getId())
                .eventId(delivery.getEventId())
                .subscriptionId(delivery.getSubscriptionId())
                .reconView(delivery.getReconView())
                .channelType(delivery.getChannelType())
                .endpointUrl(delivery.getEndpointUrl())
                .deliveryStatus(delivery.getDeliveryStatus())
                .responseStatusCode(delivery.getResponseStatusCode())
                .errorMessage(delivery.getErrorMessage())
                .createdAt(delivery.getCreatedAt())
                .lastAttemptAt(delivery.getLastAttemptAt())
                .deliveredAt(delivery.getDeliveredAt())
                .build();
    }

    private AlertEscalationPolicyDto toDto(AlertEscalationPolicy policy) {
        return AlertEscalationPolicyDto.builder()
                .id(policy.getId())
                .policyName(policy.getPolicyName())
                .reconView(policy.getReconView())
                .metricKey(policy.getMetricKey())
                .severityThreshold(policy.getSeverityThreshold())
                .storeId(policy.getStoreId())
                .wkstnId(policy.getWkstnId())
                .escalationAfterMinutes(policy.getEscalationAfterMinutes())
                .destinationType(policy.getDestinationType())
                .destinationKey(policy.getDestinationKey())
                .active(policy.isActive())
                .description(policy.getDescription())
                .createdBy(policy.getCreatedBy())
                .updatedBy(policy.getUpdatedBy())
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .build();
    }

    private AlertEscalationHistoryDto toDto(AlertEscalationHistory history) {
        return AlertEscalationHistoryDto.builder()
                .id(history.getId())
                .eventId(history.getEventId())
                .policyId(history.getPolicyId())
                .reconView(history.getReconView())
                .ruleName(history.getRuleName())
                .severity(history.getSeverity())
                .destinationType(history.getDestinationType())
                .destinationKey(history.getDestinationKey())
                .escalationStatus(history.getEscalationStatus())
                .errorMessage(history.getErrorMessage())
                .escalatedAt(history.getEscalatedAt())
                .build();
    }

    private AlertUserSubscriptionDto toDto(AlertUserSubscription subscription) {
        return AlertUserSubscriptionDto.builder()
                .id(subscription.getId())
                .username(subscription.getUsername())
                .reconView(subscription.getReconView())
                .metricKey(subscription.getMetricKey())
                .severityThreshold(subscription.getSeverityThreshold())
                .channelType(subscription.getChannelType())
                .endpointUrl(subscription.getEndpointUrl())
                .storeId(subscription.getStoreId())
                .wkstnId(subscription.getWkstnId())
                .active(subscription.isActive())
                .description(subscription.getDescription())
                .createdAt(subscription.getCreatedAt())
                .updatedAt(subscription.getUpdatedAt())
                .build();
    }

    private AlertDigestSubscriptionDto toDto(AlertDigestSubscription subscription) {
        return AlertDigestSubscriptionDto.builder()
                .id(subscription.getId())
                .digestName(subscription.getDigestName())
                .reconView(subscription.getReconView())
                .scopeType(subscription.getScopeType())
                .scopeKey(subscription.getScopeKey())
                .severityThreshold(subscription.getSeverityThreshold())
                .recipientType(subscription.getRecipientType())
                .recipientKey(subscription.getRecipientKey())
                .active(subscription.isActive())
                .description(subscription.getDescription())
                .createdBy(subscription.getCreatedBy())
                .updatedBy(subscription.getUpdatedBy())
                .createdAt(subscription.getCreatedAt())
                .updatedAt(subscription.getUpdatedAt())
                .build();
    }

    private AlertDigestRunDto toDto(AlertDigestRun run) {
        return AlertDigestRunDto.builder()
                .id(run.getId())
                .subscriptionId(run.getSubscriptionId())
                .reconView(run.getReconView())
                .scopeType(run.getScopeType())
                .scopeKey(run.getScopeKey())
                .recipientSummary(run.getRecipientSummary())
                .runStatus(run.getRunStatus())
                .itemCount(run.getItemCount())
                .digestSubject(run.getDigestSubject())
                .errorMessage(run.getErrorMessage())
                .createdAt(run.getCreatedAt())
                .deliveredAt(run.getDeliveredAt())
                .build();
    }

    private AlertAnomalyRuleDto toDto(AlertAnomalyRule rule) {
        return AlertAnomalyRuleDto.builder()
                .id(rule.getId())
                .ruleName(rule.getRuleName())
                .reconView(rule.getReconView())
                .metricKey(rule.getMetricKey())
                .anomalyType(rule.getAnomalyType())
                .percentChangeThreshold(rule.getPercentChangeThreshold())
                .minBaselineValue(rule.getMinBaselineValue())
                .lookbackDays(rule.getLookbackDays())
                .cooldownMinutes(rule.getCooldownMinutes())
                .severity(rule.getSeverity())
                .storeId(rule.getStoreId())
                .active(rule.isActive())
                .description(rule.getDescription())
                .createdBy(rule.getCreatedBy())
                .updatedBy(rule.getUpdatedBy())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    private AlertSmsSubscriptionDto toDto(AlertSmsSubscription subscription) {
        return AlertSmsSubscriptionDto.builder()
                .id(subscription.getId())
                .subscriptionName(subscription.getSubscriptionName())
                .reconView(subscription.getReconView())
                .metricKey(subscription.getMetricKey())
                .severityThreshold(subscription.getSeverityThreshold())
                .phoneNumber(subscription.getPhoneNumber())
                .storeId(subscription.getStoreId())
                .wkstnId(subscription.getWkstnId())
                .active(subscription.isActive())
                .description(subscription.getDescription())
                .createdBy(subscription.getCreatedBy())
                .updatedBy(subscription.getUpdatedBy())
                .createdAt(subscription.getCreatedAt())
                .updatedAt(subscription.getUpdatedAt())
                .build();
    }

    private AlertSmsDeliveryDto toDto(AlertSmsDelivery delivery) {
        return AlertSmsDeliveryDto.builder()
                .id(delivery.getId())
                .eventId(delivery.getEventId())
                .subscriptionId(delivery.getSubscriptionId())
                .reconView(delivery.getReconView())
                .phoneNumber(delivery.getPhoneNumber())
                .providerName(delivery.getProviderName())
                .deliveryStatus(delivery.getDeliveryStatus())
                .responseStatusCode(delivery.getResponseStatusCode())
                .errorMessage(delivery.getErrorMessage())
                .createdAt(delivery.getCreatedAt())
                .lastAttemptAt(delivery.getLastAttemptAt())
                .deliveredAt(delivery.getDeliveredAt())
                .build();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String upperOrNull(String value) {
        return value == null ? null : value.toUpperCase();
    }
}
