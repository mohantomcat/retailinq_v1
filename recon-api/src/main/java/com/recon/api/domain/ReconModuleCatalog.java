package com.recon.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "recon_module_catalog", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconModuleCatalog {

    @Id
    private UUID id;

    @Column(name = "recon_view", nullable = false, unique = true)
    private String reconView;

    @Column(name = "tab_id", nullable = false, unique = true)
    private String tabId;

    @Column(name = "module_label", nullable = false)
    private String moduleLabel;

    @Column(name = "target_system")
    private String targetSystem;

    @Column(name = "permission_code", nullable = false, unique = true)
    private String permissionCode;

    @Column(name = "group_code", nullable = false)
    private String groupCode;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "configuration_module_id")
    private String configurationModuleId;

    @Column(name = "operations_module_ids")
    private String operationsModuleIds;

    @Column(name = "operations_module_catalog_json")
    private String operationsModuleCatalogJson;

    @Column(name = "integration_connector_keys")
    private String integrationConnectorKeys;

    @Column(name = "is_active", nullable = false)
    @Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
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
