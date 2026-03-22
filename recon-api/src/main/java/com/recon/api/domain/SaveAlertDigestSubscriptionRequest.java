package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveAlertDigestSubscriptionRequest {
    private String digestName;
    private String reconView;
    private String scopeType;
    private String scopeKey;
    private String severityThreshold;
    private String recipientType;
    private String recipientKey;
    private Boolean active;
    private String description;
}
