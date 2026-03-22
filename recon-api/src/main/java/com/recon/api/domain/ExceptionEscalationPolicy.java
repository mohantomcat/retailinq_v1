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
@Table(name = "exception_escalation_policies", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionEscalationPolicy {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "policy_name", nullable = false)
    private String policyName;

    @Column(name = "recon_view", nullable = false)
    private String reconView;

    @Column(name = "min_severity")
    private String minSeverity;

    @Column(name = "min_impact_score")
    private Integer minImpactScore;

    @Column(name = "trigger_on_sla_breach", nullable = false)
    @Builder.Default
    private boolean triggerOnSlaBreach = false;

    @Column(name = "aging_hours")
    private Integer agingHours;

    @Column(name = "inactivity_hours")
    private Integer inactivityHours;

    @Column(name = "escalate_to_username")
    private String escalateToUsername;

    @Column(name = "escalate_to_role_name")
    private String escalateToRoleName;

    @Column(name = "target_severity")
    private String targetSeverity;

    @Column(name = "note_template")
    private String noteTemplate;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "description")
    private String description;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "updated_by")
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
