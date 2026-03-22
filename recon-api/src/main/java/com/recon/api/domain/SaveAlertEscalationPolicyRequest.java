package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveAlertEscalationPolicyRequest {
    private String policyName;
    private String reconView;
    private String metricKey;
    private String severityThreshold;
    private String storeId;
    private String wkstnId;
    private Integer escalationAfterMinutes;
    private String destinationType;
    private String destinationKey;
    private Boolean active;
    private String description;
}
