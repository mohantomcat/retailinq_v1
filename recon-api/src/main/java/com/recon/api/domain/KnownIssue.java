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
@Table(name = "known_issues", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnownIssue {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "issue_key", nullable = false)
    private String issueKey;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "issue_summary")
    private String issueSummary;

    @Column(name = "recon_view")
    private String reconView;

    @Column(name = "recon_status")
    private String reconStatus;

    @Column(name = "reason_code")
    private String reasonCode;

    @Column(name = "root_cause_category")
    private String rootCauseCategory;

    @Column(name = "store_id")
    private String storeId;

    @Column(name = "match_keywords")
    private String matchKeywords;

    @Column(name = "probable_cause", nullable = false)
    private String probableCause;

    @Column(name = "recommended_action", nullable = false)
    private String recommendedAction;

    @Column(name = "escalation_guidance")
    private String escalationGuidance;

    @Column(name = "resolver_notes")
    private String resolverNotes;

    @Column(name = "priority_weight", nullable = false)
    @Builder.Default
    private Integer priorityWeight = 100;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

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
        if (priorityWeight == null || priorityWeight <= 0) {
            priorityWeight = 100;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        if (priorityWeight == null || priorityWeight <= 0) {
            priorityWeight = 100;
        }
        updatedAt = LocalDateTime.now();
    }
}
