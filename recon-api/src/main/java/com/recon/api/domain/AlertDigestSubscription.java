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
@Table(name = "alert_digest_subscriptions", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertDigestSubscription {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "digest_name", nullable = false)
    private String digestName;

    @Column(name = "recon_view", nullable = false)
    private String reconView;

    @Column(name = "scope_type", nullable = false)
    private String scopeType;

    @Column(name = "scope_key")
    private String scopeKey;

    @Column(name = "severity_threshold")
    private String severityThreshold;

    @Column(name = "recipient_type", nullable = false)
    private String recipientType;

    @Column(name = "recipient_key", nullable = false)
    private String recipientKey;

    @Column(name = "active", nullable = false)
    private boolean active;

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
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
