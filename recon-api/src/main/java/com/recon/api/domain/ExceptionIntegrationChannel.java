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
@Table(name = "exception_integration_channels", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionIntegrationChannel {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "channel_name", nullable = false)
    private String channelName;

    @Column(name = "channel_type", nullable = false)
    private String channelType;

    @Column(name = "channel_group", nullable = false)
    private String channelGroup;

    @Column(name = "recon_view")
    private String reconView;

    @Column(name = "endpoint_url")
    private String endpointUrl;

    @Column(name = "recipient_email")
    private String recipientEmail;

    @Column(name = "headers_json", columnDefinition = "text")
    private String headersJson;

    @Column(name = "default_project_key")
    private String defaultProjectKey;

    @Column(name = "default_issue_type")
    private String defaultIssueType;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "inbound_sync_enabled", nullable = false)
    @Builder.Default
    private boolean inboundSyncEnabled = false;

    @Column(name = "inbound_shared_secret")
    private String inboundSharedSecret;

    @Column(name = "auto_create_on_case_open", nullable = false)
    @Builder.Default
    private boolean autoCreateOnCaseOpen = false;

    @Column(name = "auto_create_on_escalation", nullable = false)
    @Builder.Default
    private boolean autoCreateOnEscalation = false;

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
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
