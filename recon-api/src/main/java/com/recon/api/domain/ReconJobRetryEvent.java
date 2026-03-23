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

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "recon_job_retry_event", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconJobRetryEvent {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "job_definition_id", nullable = false)
    private UUID jobDefinitionId;

    @Column(name = "failed_run_id", nullable = false)
    private UUID failedRunId;

    @Column(name = "retry_run_id")
    private UUID retryRunId;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(name = "scheduled_for", nullable = false)
    private LocalDateTime scheduledFor;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "retry_status", nullable = false)
    private String retryStatus;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (retryStatus == null || retryStatus.isBlank()) {
            retryStatus = "PENDING";
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
