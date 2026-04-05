package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
public class UserDto {
    private UUID id;
    private String username;
    private String email;
    private String fullName;
    private String tenantId;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;
    private Set<String> storeIds;
    private Set<String> effectiveStoreIds;
    private boolean allStoreAccess;
    private AccessScopeSummaryDto accessScope;
    private List<RoleDto> roles;
    private Set<String> permissions;
    private List<ReconModuleDto> accessibleModules;
}
