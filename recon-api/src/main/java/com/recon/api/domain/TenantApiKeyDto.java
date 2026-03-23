package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class TenantApiKeyDto {
    private UUID id;
    private String tenantId;
    private String keyName;
    private String keyPrefix;
    private String description;
    private List<String> permissionCodes;
    private boolean active;
    private boolean allStoreAccess;
    private List<String> allowedStoreIds;
    private LocalDateTime lastUsedAt;
    private String lastUsedBy;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
