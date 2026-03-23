package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveTenantBrandingRequest {
    private String appName;
    private String lightLogoData;
    private String darkLogoData;
    private String primaryColor;
    private String secondaryColor;
}
