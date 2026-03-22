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
@Table(name = "audit_ledger_entries", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLedgerEntry {

    @Id
    private UUID id;

    @Column(name = "entry_number", insertable = false, updatable = false)
    private Long entryNumber;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "module_key", nullable = false)
    private String moduleKey;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_key", nullable = false)
    private String entityKey;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "summary")
    private String summary;

    @Column(name = "actor")
    private String actor;

    @Column(name = "reason")
    private String reason;

    @Column(name = "status")
    private String status;

    @Column(name = "reference_key")
    private String referenceKey;

    @Column(name = "control_family")
    private String controlFamily;

    @Column(name = "evidence_tags")
    private String evidenceTags;

    @Column(name = "before_state")
    private String beforeState;

    @Column(name = "after_state")
    private String afterState;

    @Column(name = "metadata_json")
    private String metadataJson;

    @Column(name = "event_at", nullable = false)
    private LocalDateTime eventAt;

    @Column(name = "previous_hash")
    private String previousHash;

    @Column(name = "event_hash", nullable = false)
    private String eventHash;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (eventAt == null) {
            eventAt = LocalDateTime.now();
        }
    }
}
