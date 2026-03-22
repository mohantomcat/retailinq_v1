package com.recon.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "exception_approval_requests", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionApprovalRequest {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "case_id", nullable = false)
    private ExceptionCase exceptionCase;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "policy_id", nullable = false)
    private ExceptionClosurePolicy policy;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "transaction_key", nullable = false)
    private String transactionKey;

    @Column(name = "recon_view", nullable = false)
    private String reconView;

    @Column(name = "previous_case_status", nullable = false)
    private String previousCaseStatus;

    @Column(name = "requested_case_status", nullable = false)
    private String requestedCaseStatus;

    @Column(name = "requested_severity", nullable = false)
    private String requestedSeverity;

    @Column(name = "requested_reason_code")
    private String requestedReasonCode;

    @Column(name = "requested_root_cause_category")
    private String requestedRootCauseCategory;

    @Column(name = "requested_assignee_username")
    private String requestedAssigneeUsername;

    @Column(name = "requested_assigned_role_name")
    private String requestedAssignedRoleName;

    @Column(name = "requested_notes")
    private String requestedNotes;

    @Column(name = "closure_comment")
    private String closureComment;

    @Column(name = "approver_role_name")
    private String approverRoleName;

    @Column(name = "request_status", nullable = false)
    private String requestStatus;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "decision_by")
    private String decisionBy;

    @Column(name = "decision_notes")
    private String decisionNotes;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "decision_at")
    private LocalDateTime decisionAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (requestStatus == null || requestStatus.isBlank()) {
            requestStatus = "PENDING";
        }
        requestedAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
