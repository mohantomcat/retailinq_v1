package com.recon.api.domain;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class AssignUserOrganizationScopesRequest {
    private List<UUID> organizationUnitIds;
    private Boolean includeDescendants;
}
