package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertsResponse {
    private AlertSummaryDto summary;
    private List<AlertRuleDto> rules;
    private List<AlertEventDto> events;
    private List<AlertEmailSubscriptionDto> subscriptions;
    private List<AlertEmailDeliveryDto> deliveries;
    private List<AlertWebhookSubscriptionDto> webhookSubscriptions;
    private List<AlertWebhookDeliveryDto> webhookDeliveries;
    private List<AlertEscalationPolicyDto> escalationPolicies;
    private List<AlertEscalationHistoryDto> escalationHistory;
    private List<AlertUserSubscriptionDto> personalSubscriptions;
    private List<AlertDigestSubscriptionDto> digestSubscriptions;
    private List<AlertDigestRunDto> digestRuns;
    private List<AlertAnomalyRuleDto> anomalyRules;
    private List<AlertSmsSubscriptionDto> smsSubscriptions;
    private List<AlertSmsDeliveryDto> smsDeliveries;
}
