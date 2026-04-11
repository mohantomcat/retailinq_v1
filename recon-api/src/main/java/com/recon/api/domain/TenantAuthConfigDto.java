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
    private String samlAcsUrl;
    private String samlSsoUrl;
    private String samlIdpEntityId;
    private String samlIdpMetadataUrl;
    private String samlIdpVerificationCertificate;
    private boolean apiKeyAuthEnabled;
    private boolean autoProvisionUsers;
    private String allowedEmailDomains;
    private String oidcUsernameClaim;
    private String oidcEmailClaim;
    private String oidcGroupsClaim;
    private String samlEmailAttribute;
    private String samlGroupsAttribute;
    private String samlUsernameAttribute;
    private boolean scimEnabled;
    private String scimBearerTokenRef;
    private boolean scimGroupPushEnabled;
    private String scimDeprovisionPolicy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
