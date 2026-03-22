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
@Table(name = "exception_suppression_rules", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionSuppressionRule {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(name = "recon_view", nullable = false)
    private String reconView;

    @Column(name = "recon_status")
    private String reconStatus;

    @Column(name = "max_severity", nullable = false)
    private String maxSeverity;

    @Column(name = "root_cause_category")
    private String rootCauseCategory;

    @Column(name = "reason_code")
    private String reasonCode;

    @Column(name = "store_id")
    private String storeId;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "max_value_at_risk")
    private BigDecimal maxValueAtRisk;

    @Column(name = "min_repeat_count")
    private Integer minRepeatCount;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

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
        if (maxSeverity == null || maxSeverity.isBlank()) {
            maxSeverity = "LOW";
        }
        if (actionType == null || actionType.isBlank()) {
            actionType = "SUPPRESS_QUEUE";
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
