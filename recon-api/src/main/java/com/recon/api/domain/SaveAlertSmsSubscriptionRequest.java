package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveAlertSmsSubscriptionRequest {
    private String subscriptionName;
    private String reconView;
    private String metricKey;
    private String severityThreshold;
    private String phoneNumber;
    private String storeId;
    private String wkstnId;
    private Boolean active;
    private String description;
}
