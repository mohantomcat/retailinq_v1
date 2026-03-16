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
public class AlertRuleDto {
    private UUID id;
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
    private boolean active;
    private String description;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
