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
@Table(name = "exception_external_ticket_sync_events", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionExternalTicketSyncEvent {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private ExceptionExternalTicket ticket;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    private ExceptionIntegrationChannel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private ExceptionCase exceptionCase;

    @Column(name = "transaction_key")
    private String transactionKey;

    @Column(name = "incident_key")
    private String incidentKey;

    @Column(name = "recon_view")
    private String reconView;

    @Column(name = "store_id")
    private String storeId;

    @Column(name = "external_reference")
    private String externalReference;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "external_status")
    private String externalStatus;

    @Column(name = "status_note", columnDefinition = "text")
    private String statusNote;

    @Column(name = "external_updated_by")
    private String externalUpdatedBy;

    @Column(name = "payload", columnDefinition = "text")
    private String payload;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

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
        if (syncedAt == null) {
            syncedAt = createdAt;
        }
    }
}
