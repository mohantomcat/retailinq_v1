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
@Table(name = "recon_job_definition", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconJobDefinition {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "job_name", nullable = false)
    private String jobName;

    @Column(name = "recon_view", nullable = false)
    private String reconView;

    @Column(name = "cron_expression", nullable = false)
    private String cronExpression;

    @Column(name = "job_timezone", nullable = false)
    private String jobTimezone;

    @Column(name = "window_type", nullable = false)
    private String windowType;

    @Column(name = "end_of_day_local_time")
    private String endOfDayLocalTime;

    @Column(name = "business_date_offset_days", nullable = false)
    private Integer businessDateOffsetDays;

    @Column(name = "max_retry_attempts", nullable = false)
    private Integer maxRetryAttempts;

    @Column(name = "retry_delay_minutes", nullable = false)
    private Integer retryDelayMinutes;

    @Column(name = "allow_concurrent_runs", nullable = false)
    private boolean allowConcurrentRuns;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope_store_ids", columnDefinition = "jsonb")
    private String scopeStoreIds;

    @Column(name = "notification_channel_type")
    private String notificationChannelType;

    @Column(name = "notification_endpoint")
    private String notificationEndpoint;

    @Column(name = "notification_email")
    private String notificationEmail;

    @Column(name = "notify_on_success", nullable = false)
    private boolean notifyOnSuccess;

    @Column(name = "notify_on_failure", nullable = false)
    private boolean notifyOnFailure;

    @Column(name = "last_scheduled_at")
    private LocalDateTime lastScheduledAt;

    @Column(name = "next_scheduled_at")
    private LocalDateTime nextScheduledAt;

    @Column(name = "last_run_started_at")
    private LocalDateTime lastRunStartedAt;

    @Column(name = "last_run_completed_at")
    private LocalDateTime lastRunCompletedAt;

    @Column(name = "last_run_status")
    private String lastRunStatus;

    @Column(name = "last_run_message")
    private String lastRunMessage;

    @Column(name = "created_by")
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
        if (jobTimezone == null || jobTimezone.isBlank()) {
            jobTimezone = "UTC";
        }
        if (windowType == null || windowType.isBlank()) {
            windowType = "CONTINUOUS";
        }
        if (businessDateOffsetDays == null) {
            businessDateOffsetDays = 0;
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
