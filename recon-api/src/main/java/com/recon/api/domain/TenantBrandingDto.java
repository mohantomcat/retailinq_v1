package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TenantBrandingDto {
    private String tenantId;
    private String appName;
    private String lightLogoData;
    private String darkLogoData;
    private String primaryColor;
    private String secondaryColor;
    private boolean customized;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
