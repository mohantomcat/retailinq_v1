package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TenantAccessCenterResponse {
    private TenantAuthConfigDto authConfig;
    private List<OidcGroupRoleMappingDto> oidcGroupRoleMappings;
    private List<RoleDto> roles;
    private List<TenantApiKeyDto> apiKeys;
    private List<String> storeCatalog;
    private List<ReconGroupSelectionDto> reconGroups;
    private List<SystemEndpointProfileDto> systemEndpointProfiles;
}
