package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class OrganizationUnitDto {
    private UUID id;
    private String tenantId;
    private String unitKey;
    private String unitName;
    private String unitType;
    private UUID parentUnitId;
    private String parentUnitKey;
    private String parentUnitName;
    private String storeId;
    private Integer sortOrder;
    private boolean active;
    private long childCount;
    private long assignedUserCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
