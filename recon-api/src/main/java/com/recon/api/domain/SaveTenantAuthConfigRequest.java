package com.recon.api.domain;

import lombok.Data;

@Data
public class SaveTenantAuthConfigRequest {
    private Boolean localLoginEnabled;
    private String preferredLoginMode;
    private Boolean oidcEnabled;
    private String oidcDisplayName;
    private String oidcIssuerUrl;
    private String oidcClientId;
    private Boolean samlEnabled;
    private String samlDisplayName;
    private String samlEntityId;
    private String samlSsoUrl;
    private Boolean apiKeyAuthEnabled;
}
