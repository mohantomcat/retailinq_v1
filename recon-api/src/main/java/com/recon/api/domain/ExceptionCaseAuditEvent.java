package com.recon.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "exception_case_audit_events", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionCaseAuditEvent {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exception_case_id", nullable = false)
    private ExceptionCase exceptionCase;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "transaction_key", nullable = false)
    private String transactionKey;

    @Column(name = "recon_view", nullable = false)
    private String reconView;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "summary")
    private String summary;

    @Column(name = "actor")
    private String actor;

    @Column(name = "status")
    private String status;

    @Column(name = "changed_fields")
    private String changedFields;

    @Column(name = "before_snapshot")
    private String beforeSnapshot;

    @Column(name = "after_snapshot")
    private String afterSnapshot;

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
