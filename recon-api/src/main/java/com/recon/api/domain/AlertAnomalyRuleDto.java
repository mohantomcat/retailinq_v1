package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertAnomalyRuleDto {
    private UUID id;
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
    private boolean active;
    private String description;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
