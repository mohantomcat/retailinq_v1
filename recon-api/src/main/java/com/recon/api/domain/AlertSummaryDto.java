package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertSummaryDto {
    private long activeRules;
    private long openEvents;
    private long acknowledgedEvents;
    private long resolvedEvents;
    private long criticalEvents;
    private long activeSubscriptions;
    private long activeWebhookSubscriptions;
    private long activeEscalationPolicies;
    private long activePersonalSubscriptions;
    private long activeDigestSubscriptions;
    private long recentDigestRuns;
    private long activeAnomalyRules;
    private long openAnomalyEvents;
    private long activeSmsSubscriptions;
    private long escalatedEvents;
    private long sentEmailDeliveries;
    private long failedEmailDeliveries;
    private long sentWebhookDeliveries;
    private long failedWebhookDeliveries;
    private long sentSmsDeliveries;
    private long failedSmsDeliveries;
    private long failedNotificationDeliveries;
}
