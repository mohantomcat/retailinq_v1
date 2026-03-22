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
@Table(name = "known_issue_feedback", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnownIssueFeedback {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "known_issue_id", nullable = false)
    private KnownIssue knownIssue;

    @Column(name = "context_key", nullable = false)
    private String contextKey;

    @Column(name = "transaction_key")
    private String transactionKey;

    @Column(name = "recon_view")
    private String reconView;

    @Column(name = "incident_key")
    private String incidentKey;

    @Column(name = "store_id")
    private String storeId;

    @Column(name = "source_view")
    private String sourceView;

    @Column(name = "helpful", nullable = false)
    private boolean helpful;

    @Column(name = "feedback_notes")
    private String feedbackNotes;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

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
