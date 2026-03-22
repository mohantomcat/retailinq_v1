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
public class AlertEventDto {
    private UUID id;
    private UUID ruleId;
    private UUID anomalyRuleId;
    private String ruleName;
    private String reconView;
    private String metricKey;
    private String severity;
    private String storeId;
    private String wkstnId;
    private String scopeKey;
    private String alertStatus;
    private BigDecimal metricValue;
    private BigDecimal thresholdValue;
    private String detectionType;
    private String anomalyDirection;
    private BigDecimal baselineValue;
    private BigDecimal deltaPercentage;
    private String eventMessage;
    private Integer triggerCount;
    private LocalDateTime firstTriggeredAt;
    private LocalDateTime lastTriggeredAt;
    private String acknowledgedBy;
    private LocalDateTime acknowledgedAt;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
}
