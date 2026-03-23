package com.recon.api.domain;

import lombok.Data;

import java.util.List;

@Data
public class CreateTenantApiKeyRequest {
    private String keyName;
    private String description;
    private List<String> permissionCodes;
    private Boolean allStoreAccess;
    private List<String> allowedStoreIds;
}
