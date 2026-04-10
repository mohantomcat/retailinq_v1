package com.recon.api.domain;

import lombok.Data;

import java.util.UUID;

@Data
public class OidcGroupRoleMappingAssignmentRequest {
    private String oidcGroup;
    private UUID roleId;
    private Boolean active;
}
