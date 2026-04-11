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
    private String samlAcsUrl;
    private String samlSsoUrl;
    private String samlIdpEntityId;
    private String samlIdpMetadataUrl;
    private String samlIdpVerificationCertificate;
    private Boolean apiKeyAuthEnabled;
    private Boolean autoProvisionUsers;
    private String allowedEmailDomains;
    private String oidcUsernameClaim;
    private String oidcEmailClaim;
    private String oidcGroupsClaim;
    private String samlEmailAttribute;
    private String samlGroupsAttribute;
    private String samlUsernameAttribute;
    private Boolean scimEnabled;
    private String scimBearerTokenRef;
    private Boolean scimGroupPushEnabled;
    private String scimDeprovisionPolicy;
    private Boolean managerAccessReviewRemindersEnabled;
    private Integer managerAccessReviewReminderIntervalDays;
    private Integer governanceNotificationMaxAttempts;
    private Integer governanceNotificationBackoffMinutes;
    private String managerAccessReviewAdditionalEmails;
    private String managerAccessReviewTeamsWebhookUrl;
    private String managerAccessReviewSlackWebhookUrl;
    private Boolean managerAccessReviewEscalationEnabled;
    private Integer managerAccessReviewEscalationAfterDays;
    private String managerAccessReviewEscalationEmailRecipients;
    private String managerAccessReviewEscalationTeamsWebhookUrl;
    private String managerAccessReviewEscalationSlackWebhookUrl;
    private Boolean privilegedActionAlertsEnabled;
    private String privilegedActionAlertEmailRecipients;
    private String privilegedActionAlertTeamsWebhookUrl;
    private String privilegedActionAlertSlackWebhookUrl;
    private String managerAccessReviewReminderSubjectTemplate;
    private String managerAccessReviewReminderBodyTemplate;
    private String managerAccessReviewEscalationSubjectTemplate;
    private String managerAccessReviewEscalationBodyTemplate;
    private String privilegedActionAlertSubjectTemplate;
    private String privilegedActionAlertBodyTemplate;
}
