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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "recon_job_step_definition", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconJobStepDefinition {

    @Id
    private UUID id;

    @Column(name = "job_definition_id", nullable = false)
    private UUID jobDefinitionId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "step_label", nullable = false)
    private String stepLabel;

    @Column(name = "step_type", nullable = false)
    private String stepType;

    @Column(name = "module_id")
    private String moduleId;

    @Column(name = "action_key")
    private String actionKey;

    @Column(name = "depends_on_step_id")
    private UUID dependsOnStepId;

    @Column(name = "settle_delay_seconds")
    private Integer settleDelaySeconds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "step_config", columnDefinition = "jsonb")
    private String stepConfig;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
