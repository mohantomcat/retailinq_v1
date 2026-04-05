package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@Builder
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private String userId;
    private String username;
    private String fullName;
    private String email;
    private String tenantId;
    private Set<String> permissions;
    private Set<String> storeIds;
    private Set<String> effectiveStoreIds;
    private boolean allStoreAccess;
    private AccessScopeSummaryDto accessScope;
    private String authMode;
    private List<RoleDto> roles;
    private List<ReconModuleDto> accessibleModules;
}
