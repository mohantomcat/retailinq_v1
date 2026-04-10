package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TenantAuthConfigDto {
    private String tenantId;
    private boolean localLoginEnabled;
    private String preferredLoginMode;
    private boolean oidcEnabled;
    private String oidcDisplayName;
    private String oidcIssuerUrl;
    private String oidcClientId;
    private String oidcRedirectUri;
    private String oidcScopes;
    private String oidcClientSecretRef;
    private boolean samlEnabled;
    private String samlDisplayName;
    private String samlEntityId;
    private String samlSsoUrl;
    private boolean apiKeyAuthEnabled;
    private boolean autoProvisionUsers;
    private String allowedEmailDomains;
    private String oidcUsernameClaim;
    private String oidcEmailClaim;
    private String oidcGroupsClaim;
    private String samlEmailAttribute;
    private String samlGroupsAttribute;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
