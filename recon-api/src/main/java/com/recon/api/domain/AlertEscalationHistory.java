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
@Table(name = "alert_escalation_history", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEscalationHistory {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "recon_view", nullable = false)
    private String reconView;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "destination_type", nullable = false)
    private String destinationType;

    @Column(name = "destination_key", nullable = false)
    private String destinationKey;

    @Column(name = "escalation_status", nullable = false)
    private String escalationStatus;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "escalated_at", nullable = false)
    private LocalDateTime escalatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (escalatedAt == null) {
            escalatedAt = LocalDateTime.now();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
