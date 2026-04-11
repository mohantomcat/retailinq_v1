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
@Table(name = "access_governance_notification_jobs", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessGovernanceNotificationJob {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "notification_type", nullable = false)
    private String notificationType;

    @Column(name = "channel_type", nullable = false)
    private String channelType;

    @Column(name = "target_key", nullable = false)
    private String targetKey;

    @Column(name = "notification_context_key")
    private String notificationContextKey;

    @Column(name = "reference_user_ids")
    private String referenceUserIds;

    @Column(name = "payload_data", columnDefinition = "text")
    private String payloadData;

    @Column(name = "notification_status", nullable = false)
    @Builder.Default
    private String notificationStatus = "PENDING";

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 3;

    @Column(name = "backoff_minutes", nullable = false)
    @Builder.Default
    private int backoffMinutes = 15;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

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
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (nextAttemptAt == null) {
            nextAttemptAt = LocalDateTime.now();
        }
        if (notificationStatus == null || notificationStatus.isBlank()) {
            notificationStatus = "PENDING";
        }
        if (maxAttempts < 1) {
            maxAttempts = 3;
        }
        if (backoffMinutes < 1) {
            backoffMinutes = 15;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
        if (maxAttempts < 1) {
            maxAttempts = 3;
        }
        if (backoffMinutes < 1) {
            backoffMinutes = 15;
        }
    }
}
