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

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "alert_escalation_policies", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEscalationPolicy {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "policy_name", nullable = false)
    private String policyName;

    @Column(name = "recon_view", nullable = false)
    private String reconView;

    @Column(name = "metric_key")
    private String metricKey;

    @Column(name = "severity_threshold")
    private String severityThreshold;

    @Column(name = "store_id")
    private String storeId;

    @Column(name = "wkstn_id")
    private String wkstnId;

    @Column(name = "escalation_after_minutes", nullable = false)
    private Integer escalationAfterMinutes;

    @Column(name = "destination_type", nullable = false)
    private String destinationType;

    @Column(name = "destination_key", nullable = false)
    private String destinationKey;

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
