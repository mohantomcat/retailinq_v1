package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class EmergencyAccessGrantDto {
    private UUID id;
    private UUID userId;
    private String username;
    private String fullName;
    private List<RoleDto> roles;
    private String justification;
    private String approvalNote;
    private String grantedBy;
    private LocalDateTime grantedAt;
    private LocalDateTime expiresAt;
    private boolean active;
    private String revokedBy;
    private LocalDateTime revokedAt;
    private String revokeNote;
}
