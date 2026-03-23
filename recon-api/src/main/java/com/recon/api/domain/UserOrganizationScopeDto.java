package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class UserOrganizationScopeDto {
    private UUID organizationUnitId;
    private String unitKey;
    private String unitName;
    private String unitType;
    private boolean includeDescendants;
}
