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
@Table(name = "exception_suppression_audits", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionSuppressionAudit {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "transaction_key", nullable = false)
    private String transactionKey;

    @Column(name = "recon_view", nullable = false)
    private String reconView;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "rule_name")
    private String ruleName;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "result_status", nullable = false)
    private String resultStatus;

    @Column(name = "result_message")
    private String resultMessage;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = LocalDateTime.now();
    }
}
