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
public class SaveAlertAnomalyRuleRequest {
    private String ruleName;
    private String reconView;
    private String metricKey;
    private String anomalyType;
    private BigDecimal percentChangeThreshold;
    private BigDecimal minBaselineValue;
    private Integer lookbackDays;
    private Integer cooldownMinutes;
    private String severity;
    private String storeId;
    private Boolean active;
    private String description;
}
