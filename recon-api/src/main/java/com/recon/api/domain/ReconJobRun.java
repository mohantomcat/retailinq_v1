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
@Table(name = "recon_job_run", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconJobRun {

    @Id
    private UUID id;

    @Column(name = "job_definition_id", nullable = false)
    private UUID jobDefinitionId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "job_name", nullable = false)
    private String jobName;

    @Column(name = "recon_view", nullable = false)
    private String reconView;

    @Column(name = "trigger_type", nullable = false)
    private String triggerType;

    @Column(name = "run_status", nullable = false)
    private String runStatus;

    @Column(name = "initiated_by")
    private String initiatedBy;

    @Column(name = "parent_run_id")
    private UUID parentRunId;

    @Column(name = "root_run_id")
    private UUID rootRunId;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(name = "max_retry_attempts", nullable = false)
    private Integer maxRetryAttempts;

    @Column(name = "retry_delay_minutes", nullable = false)
    private Integer retryDelayMinutes;

    @Column(name = "retry_pending", nullable = false)
    private boolean retryPending;

    @Column(name = "scheduled_for")
    private LocalDateTime scheduledFor;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "business_date")
    private String businessDate;

    @Column(name = "window_from_business_date")
    private String windowFromBusinessDate;

    @Column(name = "window_to_business_date")
    private String windowToBusinessDate;

    @Column(name = "summary")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_payload", columnDefinition = "jsonb")
    private String resultPayload;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (attemptNumber == null || attemptNumber <= 0) {
            attemptNumber = 1;
        }
        if (maxRetryAttempts == null) {
            maxRetryAttempts = 0;
        }
        if (retryDelayMinutes == null || retryDelayMinutes <= 0) {
            retryDelayMinutes = 15;
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
