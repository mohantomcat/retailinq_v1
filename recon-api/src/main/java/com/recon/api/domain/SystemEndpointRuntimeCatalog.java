package com.recon.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "system_endpoint_runtime_catalog",
        schema = "recon",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_system_endpoint_runtime_catalog",
                columnNames = {"system_name", "endpoint_mode"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemEndpointRuntimeCatalog {

    @Id
    private UUID id;

    @Column(name = "system_name", nullable = false)
    private String systemName;

    @Column(name = "system_label", nullable = false)
    private String systemLabel;

    @Column(name = "endpoint_mode", nullable = false)
    private String endpointMode;

    @Column(name = "connector_module_id")
    private String connectorModuleId;

    @Column(name = "connector_label")
    private String connectorLabel;

    @Column(name = "integration_connector_key")
    private String integrationConnectorKey;

    @Column(name = "base_url_key")
    private String baseUrlKey;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "implemented", nullable = false)
    @Default
    private boolean implemented = true;

    @Column(name = "is_default", nullable = false)
    @Default
    private boolean defaultSelection = false;

    @Column(name = "is_active", nullable = false)
    @Default
    private boolean active = true;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
