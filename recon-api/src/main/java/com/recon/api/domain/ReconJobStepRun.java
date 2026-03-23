package com.recon.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
@Table(name = "recon_job_step_run", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconJobStepRun {

    @Id
    private UUID id;

    @Column(name = "job_run_id", nullable = false)
    private UUID jobRunId;

    @Column(name = "step_definition_id")
    private UUID stepDefinitionId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

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

    @Column(name = "run_status", nullable = false)
    private String runStatus;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "message")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", columnDefinition = "jsonb")
    private String requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
