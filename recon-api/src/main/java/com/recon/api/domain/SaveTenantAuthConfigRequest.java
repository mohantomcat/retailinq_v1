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
    private String oidcRedirectUri;
    private String oidcScopes;
    private String oidcClientSecretRef;
    private Boolean samlEnabled;
    private String samlDisplayName;
    private String samlEntityId;
    private String samlSsoUrl;
    private Boolean apiKeyAuthEnabled;
    private Boolean autoProvisionUsers;
    private String allowedEmailDomains;
    private String oidcUsernameClaim;
    private String oidcEmailClaim;
    private String oidcGroupsClaim;
    private String samlEmailAttribute;
    private String samlGroupsAttribute;
}
