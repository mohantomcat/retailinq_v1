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
@Table(name = "exception_external_tickets", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionExternalTicket {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private ExceptionCase exceptionCase;

    @Column(name = "transaction_key")
    private String transactionKey;

    @Column(name = "incident_key")
    private String incidentKey;

    @Column(name = "incident_title")
    private String incidentTitle;

    @Column(name = "recon_view", nullable = false)
    private String reconView;

    @Column(name = "store_id")
    private String storeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    private ExceptionIntegrationChannel channel;

    @Column(name = "channel_name", nullable = false)
    private String channelName;

    @Column(name = "channel_type", nullable = false)
    private String channelType;

    @Column(name = "ticket_summary", nullable = false)
    private String ticketSummary;

    @Column(name = "ticket_description", columnDefinition = "text")
    private String ticketDescription;

    @Column(name = "external_reference")
    private String externalReference;

    @Column(name = "external_url")
    private String externalUrl;

    @Column(name = "delivery_status", nullable = false)
    private String deliveryStatus;

    @Column(name = "response_status_code")
    private Integer responseStatusCode;

    @Column(name = "request_payload", columnDefinition = "text")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "text")
    private String responsePayload;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "external_status")
    private String externalStatus;

    @Column(name = "last_external_update_at")
    private LocalDateTime lastExternalUpdateAt;

    @Column(name = "last_external_updated_by")
    private String lastExternalUpdatedBy;

    @Column(name = "last_external_comment", columnDefinition = "text")
    private String lastExternalComment;

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
