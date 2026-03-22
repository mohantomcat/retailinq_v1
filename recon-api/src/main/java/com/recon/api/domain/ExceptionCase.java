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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "exception_cases", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionCase {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "transaction_key", nullable = false)
    private String transactionKey;

    @Column(name = "recon_view", nullable = false)
    private String reconView;

    @Column(name = "recon_status")
    private String reconStatus;

    @Column(name = "case_status", nullable = false)
    private String caseStatus;

    @Column(name = "reason_code")
    private String reasonCode;

    @Column(name = "root_cause_category")
    private String rootCauseCategory;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "assignee_username")
    private String assigneeUsername;

    @Column(name = "assigned_role_name")
    private String assignedRoleName;

    @Column(name = "next_action")
    private String nextAction;

    @Column(name = "next_action_due_at")
    private LocalDateTime nextActionDueAt;

    @Column(name = "handoff_note")
    private String handoffNote;

    @Column(name = "last_handoff_by")
    private String lastHandoffBy;

    @Column(name = "last_handoff_at")
    private LocalDateTime lastHandoffAt;

    @Column(name = "store_id")
    private String storeId;

    @Column(name = "wkstn_id")
    private String wkstnId;

    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(name = "auto_assigned", nullable = false)
    @Builder.Default
    private boolean autoAssigned = false;

    @Column(name = "routing_rule_id")
    private UUID routingRuleId;

    @Column(name = "routing_rule_name")
    private String routingRuleName;

    @Column(name = "playbook_id")
    private UUID playbookId;

    @Column(name = "playbook_name")
    private String playbookName;

    @Column(name = "notes")
    private String notes;

    @Column(name = "escalation_state", nullable = false)
    @Builder.Default
    private String escalationState = "NONE";

    @Column(name = "escalation_count", nullable = false)
    @Builder.Default
    private Integer escalationCount = 0;

    @Column(name = "last_escalated_by")
    private String lastEscalatedBy;

    @Column(name = "last_escalated_at")
    private LocalDateTime lastEscalatedAt;

    @Column(name = "escalation_policy_id")
    private UUID escalationPolicyId;

    @Column(name = "escalation_policy_name")
    private String escalationPolicyName;

    @Column(name = "escalation_reason")
    private String escalationReason;

    @Column(name = "reopen_reason")
    private String reopenReason;

    @Column(name = "reopen_count", nullable = false)
    @Builder.Default
    private Integer reopenCount = 0;

    @Column(name = "last_reopened_by")
    private String lastReopenedBy;

    @Column(name = "last_reopened_at")
    private LocalDateTime lastReopenedAt;

    @Column(name = "sla_target_minutes", nullable = false)
    private Integer slaTargetMinutes;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "breached_at")
    private LocalDateTime breachedAt;

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
        if (caseStatus == null || caseStatus.isBlank()) {
            caseStatus = "OPEN";
        }
        if (severity == null || severity.isBlank()) {
            severity = "MEDIUM";
        }
        if (escalationState == null || escalationState.isBlank()) {
            escalationState = "NONE";
        }
        if (escalationCount == null || escalationCount < 0) {
            escalationCount = 0;
        }
        if (reopenCount == null || reopenCount < 0) {
            reopenCount = 0;
        }
        if (slaTargetMinutes == null || slaTargetMinutes <= 0) {
            slaTargetMinutes = 480;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
