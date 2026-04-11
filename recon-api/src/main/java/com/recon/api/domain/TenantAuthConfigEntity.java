package com.recon.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_auth_config", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantAuthConfigEntity {

    @Id
    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "local_login_enabled", nullable = false)
    @Builder.Default
    private boolean localLoginEnabled = true;

    @Column(name = "preferred_login_mode", nullable = false)
    @Builder.Default
    private String preferredLoginMode = "LOCAL";

    @Column(name = "oidc_enabled", nullable = false)
    @Builder.Default
    private boolean oidcEnabled = false;

    @Column(name = "oidc_display_name")
    private String oidcDisplayName;

    @Column(name = "oidc_issuer_url")
    private String oidcIssuerUrl;

    @Column(name = "oidc_client_id")
    private String oidcClientId;

    @Column(name = "oidc_redirect_uri")
    private String oidcRedirectUri;

    @Column(name = "oidc_scopes", nullable = false)
    @Builder.Default
    private String oidcScopes = "openid profile email";

    @Column(name = "oidc_client_secret_ref")
    private String oidcClientSecretRef;

    @Column(name = "saml_enabled", nullable = false)
    @Builder.Default
    private boolean samlEnabled = false;

    @Column(name = "saml_display_name")
    private String samlDisplayName;

    @Column(name = "saml_entity_id")
    private String samlEntityId;

    @Column(name = "saml_acs_url")
    private String samlAcsUrl;

    @Column(name = "saml_sso_url")
    private String samlSsoUrl;

    @Column(name = "saml_idp_entity_id")
    private String samlIdpEntityId;

    @Column(name = "saml_idp_metadata_url")
    private String samlIdpMetadataUrl;

    @Column(name = "saml_idp_verification_certificate")
    private String samlIdpVerificationCertificate;

    @Column(name = "api_key_auth_enabled", nullable = false)
    @Builder.Default
    private boolean apiKeyAuthEnabled = false;

    @Column(name = "auto_provision_users", nullable = false)
    @Builder.Default
    private boolean autoProvisionUsers = false;

    @Column(name = "allowed_email_domains")
    private String allowedEmailDomains;

    @Column(name = "oidc_username_claim", nullable = false)
    @Builder.Default
    private String oidcUsernameClaim = "preferred_username";

    @Column(name = "oidc_email_claim", nullable = false)
    @Builder.Default
    private String oidcEmailClaim = "email";

    @Column(name = "oidc_groups_claim", nullable = false)
    @Builder.Default
    private String oidcGroupsClaim = "groups";

    @Column(name = "saml_email_attribute")
    private String samlEmailAttribute;

    @Column(name = "saml_groups_attribute")
    private String samlGroupsAttribute;

    @Column(name = "saml_username_attribute", nullable = false)
    @Builder.Default
    private String samlUsernameAttribute = "uid";

    @Column(name = "scim_enabled", nullable = false)
    @Builder.Default
    private boolean scimEnabled = false;

    @Column(name = "scim_bearer_token_ref")
    private String scimBearerTokenRef;

    @Column(name = "scim_group_push_enabled", nullable = false)
    @Builder.Default
    private boolean scimGroupPushEnabled = false;

    @Column(name = "scim_deprovision_policy", nullable = false)
    @Builder.Default
    private String scimDeprovisionPolicy = "DEACTIVATE";

    @Column(name = "manager_access_review_reminders_enabled", nullable = false)
    @Builder.Default
    private boolean managerAccessReviewRemindersEnabled = false;

    @Column(name = "manager_access_review_reminder_interval_days", nullable = false)
    @Builder.Default
    private int managerAccessReviewReminderIntervalDays = 7;

    @Column(name = "governance_notification_max_attempts", nullable = false)
    @Builder.Default
    private int governanceNotificationMaxAttempts = 3;

    @Column(name = "governance_notification_backoff_minutes", nullable = false)
    @Builder.Default
    private int governanceNotificationBackoffMinutes = 15;

    @Column(name = "manager_access_review_additional_emails")
    private String managerAccessReviewAdditionalEmails;

    @Column(name = "manager_access_review_teams_webhook_url")
    private String managerAccessReviewTeamsWebhookUrl;

    @Column(name = "manager_access_review_slack_webhook_url")
    private String managerAccessReviewSlackWebhookUrl;

    @Column(name = "manager_access_review_escalation_enabled", nullable = false)
    @Builder.Default
    private boolean managerAccessReviewEscalationEnabled = false;

    @Column(name = "manager_access_review_escalation_after_days", nullable = false)
    @Builder.Default
    private int managerAccessReviewEscalationAfterDays = 3;

    @Column(name = "manager_access_review_escalation_email_recipients")
    private String managerAccessReviewEscalationEmailRecipients;

    @Column(name = "manager_access_review_escalation_teams_webhook_url")
    private String managerAccessReviewEscalationTeamsWebhookUrl;

    @Column(name = "manager_access_review_escalation_slack_webhook_url")
    private String managerAccessReviewEscalationSlackWebhookUrl;

    @Column(name = "manager_access_review_next_tier_escalation_enabled", nullable = false)
    @Builder.Default
    private boolean managerAccessReviewNextTierEscalationEnabled = false;

    @Column(name = "manager_access_review_next_tier_escalation_after_days", nullable = false)
    @Builder.Default
    private int managerAccessReviewNextTierEscalationAfterDays = 3;

    @Column(name = "privileged_action_alerts_enabled", nullable = false)
    @Builder.Default
    private boolean privilegedActionAlertsEnabled = false;

    @Column(name = "privileged_action_alert_email_recipients")
    private String privilegedActionAlertEmailRecipients;

    @Column(name = "privileged_action_alert_teams_webhook_url")
    private String privilegedActionAlertTeamsWebhookUrl;

    @Column(name = "privileged_action_alert_slack_webhook_url")
    private String privilegedActionAlertSlackWebhookUrl;

    @Column(name = "manager_access_review_reminder_subject_template", columnDefinition = "text")
    private String managerAccessReviewReminderSubjectTemplate;

    @Column(name = "manager_access_review_reminder_body_template", columnDefinition = "text")
    private String managerAccessReviewReminderBodyTemplate;

    @Column(name = "manager_access_review_escalation_subject_template", columnDefinition = "text")
    private String managerAccessReviewEscalationSubjectTemplate;

    @Column(name = "manager_access_review_escalation_body_template", columnDefinition = "text")
    private String managerAccessReviewEscalationBodyTemplate;

    @Column(name = "privileged_action_alert_subject_template", columnDefinition = "text")
    private String privilegedActionAlertSubjectTemplate;

    @Column(name = "privileged_action_alert_body_template", columnDefinition = "text")
    private String privilegedActionAlertBodyTemplate;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (preferredLoginMode == null || preferredLoginMode.isBlank()) {
            preferredLoginMode = "LOCAL";
        }
        normalizeClaimDefaults();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
        if (preferredLoginMode == null || preferredLoginMode.isBlank()) {
            preferredLoginMode = "LOCAL";
        }
        normalizeClaimDefaults();
    }

    private void normalizeClaimDefaults() {
        if (oidcUsernameClaim == null || oidcUsernameClaim.isBlank()) {
            oidcUsernameClaim = "preferred_username";
        }
        if (oidcEmailClaim == null || oidcEmailClaim.isBlank()) {
            oidcEmailClaim = "email";
        }
        if (oidcGroupsClaim == null || oidcGroupsClaim.isBlank()) {
            oidcGroupsClaim = "groups";
        }
        if (oidcScopes == null || oidcScopes.isBlank()) {
            oidcScopes = "openid profile email";
        }
        if (samlUsernameAttribute == null || samlUsernameAttribute.isBlank()) {
            samlUsernameAttribute = "uid";
        }
        if (scimDeprovisionPolicy == null || scimDeprovisionPolicy.isBlank()) {
            scimDeprovisionPolicy = "DEACTIVATE";
        }
        if (managerAccessReviewReminderIntervalDays < 1) {
            managerAccessReviewReminderIntervalDays = 7;
        }
        if (governanceNotificationMaxAttempts < 1) {
            governanceNotificationMaxAttempts = 3;
        }
        if (governanceNotificationBackoffMinutes < 1) {
            governanceNotificationBackoffMinutes = 15;
        }
        if (managerAccessReviewEscalationAfterDays < 1) {
            managerAccessReviewEscalationAfterDays = 3;
        }
        if (managerAccessReviewNextTierEscalationAfterDays < 1) {
            managerAccessReviewNextTierEscalationAfterDays = 3;
        }
    }
}
