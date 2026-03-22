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
@Table(name = "audit_archive_batches", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditArchiveBatch {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "policy_name")
    private String policyName;

    @Column(name = "archive_reason", nullable = false)
    private String archiveReason;

    @Column(name = "export_format", nullable = false)
    private String exportFormat;

    @Column(name = "from_event_at")
    private LocalDateTime fromEventAt;

    @Column(name = "to_event_at")
    private LocalDateTime toEventAt;

    @Column(name = "entry_count", nullable = false)
    private Integer entryCount;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

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
        if (entryCount == null) {
            entryCount = 0;
        }
        if (exportFormat == null || exportFormat.isBlank()) {
            exportFormat = "JSON";
        }
    }
}
