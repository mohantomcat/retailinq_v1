package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveAlertWebhookSubscriptionRequest {
    private String subscriptionName;
    private String reconView;
    private String metricKey;
    private String severityThreshold;
    private String channelType;
    private String endpointUrl;
    private String storeId;
    private String wkstnId;
    private Boolean active;
    private String description;
}
