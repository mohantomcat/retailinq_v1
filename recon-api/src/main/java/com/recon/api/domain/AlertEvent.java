package com.recon.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "alert_events", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvent {

    @Id
    private UUID id;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "anomaly_rule_id")
    private UUID anomalyRuleId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(name = "recon_view", nullable = false)
    private String reconView;

    @Column(name = "metric_key", nullable = false)
    private String metricKey;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "scope_key", nullable = false)
    private String scopeKey;

    @Column(name = "store_id")
    private String storeId;

    @Column(name = "wkstn_id")
    private String wkstnId;

    @Column(name = "alert_status", nullable = false)
    private String alertStatus;

    @Column(name = "metric_value", nullable = false)
    private BigDecimal metricValue;

    @Column(name = "threshold_value", nullable = false)
    private BigDecimal thresholdValue;

    @Column(name = "detection_type", nullable = false)
    private String detectionType;

    @Column(name = "anomaly_direction")
    private String anomalyDirection;

    @Column(name = "baseline_value")
    private BigDecimal baselineValue;

    @Column(name = "delta_percentage")
    private BigDecimal deltaPercentage;

    @Column(name = "event_message", nullable = false)
    private String eventMessage;

    @Column(name = "trigger_count", nullable = false)
    private Integer triggerCount;

    @Column(name = "first_triggered_at", nullable = false)
    private LocalDateTime firstTriggeredAt;

    @Column(name = "last_triggered_at", nullable = false)
    private LocalDateTime lastTriggeredAt;

    @Column(name = "acknowledged_by")
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (alertStatus == null || alertStatus.isBlank()) {
            alertStatus = "OPEN";
        }
        if (detectionType == null || detectionType.isBlank()) {
            detectionType = "THRESHOLD";
        }
        if (triggerCount == null || triggerCount <= 0) {
            triggerCount = 1;
        }
        if (firstTriggeredAt == null) {
            firstTriggeredAt = LocalDateTime.now();
        }
        if (lastTriggeredAt == null) {
            lastTriggeredAt = firstTriggeredAt;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
