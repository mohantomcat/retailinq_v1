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
@Table(name = "exception_routing_rules", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionRoutingRule {

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

    @Column(name = "min_severity", nullable = false)
    private String minSeverity;

    @Column(name = "root_cause_category")
    private String rootCauseCategory;

    @Column(name = "reason_code")
    private String reasonCode;

    @Column(name = "store_id")
    private String storeId;

    @Column(name = "target_assignee_username")
    private String targetAssigneeUsername;

    @Column(name = "target_role_name")
    private String targetRoleName;

    @Column(name = "priority", nullable = false)
    private Integer priority;

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
        if (minSeverity == null || minSeverity.isBlank()) {
            minSeverity = "MEDIUM";
        }
        if (priority == null) {
            priority = 100;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
