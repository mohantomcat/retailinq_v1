package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AccessScopeSummaryDto {
    private boolean allStoreAccess;
    private List<String> directStoreIds;
    private List<String> effectiveStoreIds;
    private List<UserOrganizationScopeDto> organizationScopes;
    private String accessLabel;
}
