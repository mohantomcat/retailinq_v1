package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class OidcGroupRoleMappingDto {
    private UUID id;
    private String tenantId;
    private String oidcGroup;
    private UUID roleId;
    private String roleName;
    private boolean active;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
