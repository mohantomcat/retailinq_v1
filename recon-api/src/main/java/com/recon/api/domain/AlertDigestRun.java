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
@Table(name = "alert_digest_runs", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertDigestRun {

    @Id
    private UUID id;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "recon_view", nullable = false)
    private String reconView;

    @Column(name = "scope_type", nullable = false)
    private String scopeType;

    @Column(name = "scope_key")
    private String scopeKey;

    @Column(name = "recipient_summary")
    private String recipientSummary;

    @Column(name = "run_status", nullable = false)
    private String runStatus;

    @Column(name = "item_count", nullable = false)
    private Integer itemCount;

    @Column(name = "digest_subject", nullable = false)
    private String digestSubject;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

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
