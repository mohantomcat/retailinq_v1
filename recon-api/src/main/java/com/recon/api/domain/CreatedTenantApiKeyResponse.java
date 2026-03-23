package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreatedTenantApiKeyResponse {
    private TenantApiKeyDto apiKey;
    private String plainTextKey;
}
