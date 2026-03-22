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
@Table(name = "exception_outbound_communications", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionOutboundCommunication {

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

    @Column(name = "recipient")
    private String recipient;

    @Column(name = "subject")
    private String subject;

    @Column(name = "message_body", nullable = false, columnDefinition = "text")
    private String messageBody;

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

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = LocalDateTime.now();
    }
}
