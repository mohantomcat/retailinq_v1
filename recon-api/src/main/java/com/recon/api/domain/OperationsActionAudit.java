package com.recon.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "operations_action_audit", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationsActionAudit {
    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "module_id", nullable = false)
    private String moduleId;

    @Column(name = "action_key", nullable = false)
    private String actionKey;

    @Column(name = "action_scope", nullable = false)
    private String actionScope;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", columnDefinition = "jsonb")
    private String requestPayload;

    @Column(name = "result_status", nullable = false)
    private String resultStatus;

    @Column(name = "result_message")
    private String resultMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (actionScope == null || actionScope.isBlank()) {
            actionScope = "SAFE";
        }
        createdAt = LocalDateTime.now();
    }
}
