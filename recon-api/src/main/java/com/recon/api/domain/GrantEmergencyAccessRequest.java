package com.recon.api.domain;

import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class GrantEmergencyAccessRequest {
    private UUID userId;
    private Set<UUID> roleIds;
    private Integer expiresInHours;
    private String justification;
    private String approvalNote;
}
