package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TenantAccessGovernanceResponse {
    private int totalUsers;
    private int activeUsers;
    private int inactiveUsers;
    private int localIdentityUsers;
    private int externalIdentityUsers;
    private int usersDueForReview;
    private int usersPendingReview;
    private int pendingManagerReviews;
    private int acknowledgedManagerReviews;
    private int escalatedManagerReviews;
    private int nextTierEscalatedManagerReviews;
    private int usersWithoutManager;
    private int highPrivilegeUsers;
    private int activeEmergencyAccessUsers;
    private int usersWithoutRoles;
    private int usersMissingExternalSubject;
    private int activeApiKeys;
    private int apiKeysExpiringSoon;
    private int expiredApiKeys;
    private boolean ssoConfigured;
    private boolean ssoPreferred;
    private String preferredLoginMode;
    private List<AccessGovernanceUserFindingDto> userFindings;
    private List<AccessGovernanceApiKeyFindingDto> apiKeyFindings;
}
