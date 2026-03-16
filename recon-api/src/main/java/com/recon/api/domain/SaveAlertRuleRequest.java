package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveAlertRuleRequest {
    private String ruleName;
    private String reconView;
    private String metricKey;
    private String operator;
    private BigDecimal thresholdValue;
    private String severity;
    private String storeId;
    private String wkstnId;
    private Integer lookbackDays;
    private Integer cooldownMinutes;
    private Boolean active;
    private String description;
}
