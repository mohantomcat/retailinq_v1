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
@Table(name = "audit_retention_policies", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditRetentionPolicy {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "policy_name", nullable = false)
    private String policyName;

    @Column(name = "minimum_retention_days", nullable = false)
    private Integer minimumRetentionDays;

    @Column(name = "archive_after_days", nullable = false)
    private Integer archiveAfterDays;

    @Column(name = "legal_hold", nullable = false)
    private boolean legalHold;

    @Column(name = "default_export_format", nullable = false)
    private String defaultExportFormat;

    @Column(name = "notes")
    private String notes;

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
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        if (minimumRetentionDays == null || minimumRetentionDays <= 0) {
            minimumRetentionDays = 2555;
        }
        if (archiveAfterDays == null || archiveAfterDays <= 0) {
            archiveAfterDays = 90;
        }
        if (defaultExportFormat == null || defaultExportFormat.isBlank()) {
            defaultExportFormat = "CSV";
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
