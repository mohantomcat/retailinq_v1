package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AccessGovernanceUserFindingDto {
    private UUID userId;
    private String username;
    private String email;
    private String fullName;
    private boolean active;
    private String identityProvider;
    private String externalSubject;
    private UUID managerUserId;
    private String managerUsername;
    private String managerFullName;
    private String accessReviewStatus;
    private LocalDateTime accessReviewDueAt;
    private LocalDateTime lastAccessReviewAt;
    private String lastAccessReviewBy;
    private LocalDateTime lastLogin;
    private boolean emergencyAccessActive;
    private LocalDateTime emergencyAccessExpiresAt;
    private List<String> roleNames;
    private List<String> permissionCodes;
    private List<String> findingTypes;
}
