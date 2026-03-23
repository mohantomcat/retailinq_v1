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

@Entity
@Table(name = "tenant_branding", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantBrandingConfigEntity {

    @Id
    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "app_name")
    private String appName;

    @Column(name = "light_logo_data")
    private String lightLogoData;

    @Column(name = "dark_logo_data")
    private String darkLogoData;

    @Column(name = "primary_color", nullable = false)
    @Builder.Default
    private String primaryColor = "#3F6FD8";

    @Column(name = "secondary_color", nullable = false)
    @Builder.Default
    private String secondaryColor = "#5F7CE2";

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (primaryColor == null || primaryColor.isBlank()) {
            primaryColor = "#3F6FD8";
        }
        if (secondaryColor == null || secondaryColor.isBlank()) {
            secondaryColor = "#5F7CE2";
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
        if (primaryColor == null || primaryColor.isBlank()) {
            primaryColor = "#3F6FD8";
        }
        if (secondaryColor == null || secondaryColor.isBlank()) {
            secondaryColor = "#5F7CE2";
        }
    }
}
