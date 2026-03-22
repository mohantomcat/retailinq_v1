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
@Table(name = "alert_anomaly_rules", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertAnomalyRule {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(name = "recon_view", nullable = false)
    private String reconView;

    @Column(name = "metric_key", nullable = false)
    private String metricKey;

    @Column(name = "anomaly_type", nullable = false)
    private String anomalyType;

    @Column(name = "percent_change_threshold", nullable = false)
    private BigDecimal percentChangeThreshold;

    @Column(name = "min_baseline_value", nullable = false)
    private BigDecimal minBaselineValue;

    @Column(name = "lookback_days", nullable = false)
    private Integer lookbackDays;

    @Column(name = "cooldown_minutes", nullable = false)
    private Integer cooldownMinutes;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "store_id")
    private String storeId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "description")
    private String description;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
