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
@Table(name = "alert_sms_deliveries", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertSmsDelivery {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "recon_view", nullable = false)
    private String reconView;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Column(name = "delivery_status", nullable = false)
    private String deliveryStatus;

    @Column(name = "response_status_code")
    private Integer responseStatusCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_attempt_at", nullable = false)
    private LocalDateTime lastAttemptAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (lastAttemptAt == null) {
            lastAttemptAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void preUpdate() {
        lastAttemptAt = LocalDateTime.now();
    }
}
