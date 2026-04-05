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
@Table(name = "recon_group_catalog", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconGroupCatalog {

    @Id
    private UUID id;

    @Column(name = "group_code", nullable = false, unique = true)
    private String groupCode;

    @Column(name = "group_label", nullable = false)
    private String groupLabel;

    @Column(name = "group_description")
    private String groupDescription;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "selection_required", nullable = false)
    @Default
    private boolean selectionRequired = true;

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
