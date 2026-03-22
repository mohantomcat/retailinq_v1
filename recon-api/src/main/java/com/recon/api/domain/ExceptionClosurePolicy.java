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
@Table(name = "exception_closure_policies", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionClosurePolicy {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "policy_name", nullable = false)
    private String policyName;

    @Column(name = "recon_view", nullable = false)
    private String reconView;

    @Column(name = "target_status", nullable = false)
    private String targetStatus;

    @Column(name = "min_severity", nullable = false)
    private String minSeverity;

    @Column(name = "require_reason_code", nullable = false)
    private boolean requireReasonCode;

    @Column(name = "require_root_cause_category", nullable = false)
    private boolean requireRootCauseCategory;

    @Column(name = "require_notes", nullable = false)
    private boolean requireNotes;

    @Column(name = "require_comment", nullable = false)
    private boolean requireComment;

    @Column(name = "require_approval", nullable = false)
    private boolean requireApproval;

    @Column(name = "approver_role_name")
    private String approverRoleName;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

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
        if (minSeverity == null || minSeverity.isBlank()) {
            minSeverity = "HIGH";
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
