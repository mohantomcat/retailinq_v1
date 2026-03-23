package com.recon.api.controller;

import com.recon.api.domain.AlertsResponse;
import com.recon.api.domain.ApiResponse;
import com.recon.api.domain.SaveAlertAnomalyRuleRequest;
import com.recon.api.domain.SaveAlertDigestSubscriptionRequest;
import com.recon.api.domain.SaveAlertEscalationPolicyRequest;
import com.recon.api.domain.SaveAlertEmailSubscriptionRequest;
import com.recon.api.domain.SaveAlertRuleRequest;
import com.recon.api.domain.SaveAlertSmsSubscriptionRequest;
import com.recon.api.domain.SaveAlertUserSubscriptionRequest;
import com.recon.api.domain.SaveAlertWebhookSubscriptionRequest;
import com.recon.api.domain.UpdateAlertEventRequest;
import com.recon.api.security.ReconUserPrincipal;
import com.recon.api.service.AlertAnomalyDetectionService;
import com.recon.api.service.AlertDigestService;
import com.recon.api.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final AlertDigestService alertDigestService;
    private final AlertAnomalyDetectionService alertAnomalyDetectionService;

    @GetMapping
    public ResponseEntity<ApiResponse<AlertsResponse>> getAlerts(
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireViewAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.getAlerts(principal.getTenantId(), principal.getUserId(), principal.getUsername(), normalizeReconView(reconView), allowedReconViews(principal))));
    }

    @PostMapping("/rules")
    public ResponseEntity<ApiResponse<AlertsResponse>> createRule(
            @RequestBody SaveAlertRuleRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.saveRule(principal.getTenantId(), principal.getUserId(), null, request, principal.getUsername(), allowedReconViews(principal))));
    }

    @PutMapping("/rules/{ruleId}")
    public ResponseEntity<ApiResponse<AlertsResponse>> updateRule(
            @PathVariable UUID ruleId,
            @RequestBody SaveAlertRuleRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.saveRule(principal.getTenantId(), principal.getUserId(), ruleId, request, principal.getUsername(), allowedReconViews(principal))));
    }

    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<ApiResponse<AlertsResponse>> deleteRule(
            @PathVariable UUID ruleId,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.deleteRule(principal.getTenantId(), principal.getUserId(), principal.getUsername(), ruleId, allowedReconViews(principal))));
    }

    @PutMapping("/events/{eventId}")
    public ResponseEntity<ApiResponse<AlertsResponse>> updateEvent(
            @PathVariable UUID eventId,
            @RequestBody UpdateAlertEventRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.updateEventStatus(
                        principal.getTenantId(),
                        principal.getUserId(),
                        principal.getUsername(),
                        eventId,
                        request,
                        allowedReconViews(principal))));
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<ApiResponse<AlertsResponse>> createSubscription(
            @RequestBody SaveAlertEmailSubscriptionRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.saveSubscription(
                        principal.getTenantId(),
                        principal.getUserId(),
                        null,
                        request,
                        principal.getUsername(),
                        allowedReconViews(principal))));
    }

    @PutMapping("/subscriptions/{subscriptionId}")
    public ResponseEntity<ApiResponse<AlertsResponse>> updateSubscription(
            @PathVariable UUID subscriptionId,
            @RequestBody SaveAlertEmailSubscriptionRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.saveSubscription(
                        principal.getTenantId(),
                        principal.getUserId(),
                        subscriptionId,
                        request,
                        principal.getUsername(),
                        allowedReconViews(principal))));
    }

    @DeleteMapping("/subscriptions/{subscriptionId}")
    public ResponseEntity<ApiResponse<AlertsResponse>> deleteSubscription(
            @PathVariable UUID subscriptionId,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.deleteSubscription(
                        principal.getTenantId(),
                        principal.getUserId(),
                        principal.getUsername(),
                        subscriptionId,
                        allowedReconViews(principal))));
    }

    @PostMapping("/webhook-subscriptions")
    public ResponseEntity<ApiResponse<AlertsResponse>> createWebhookSubscription(
            @RequestBody SaveAlertWebhookSubscriptionRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.saveWebhookSubscription(
                        principal.getTenantId(),
                        principal.getUserId(),
                        null,
                        request,
                        principal.getUsername(),
                        allowedReconViews(principal))));
    }

    @PutMapping("/webhook-subscriptions/{subscriptionId}")
    public ResponseEntity<ApiResponse<AlertsResponse>> updateWebhookSubscription(
            @PathVariable UUID subscriptionId,
            @RequestBody SaveAlertWebhookSubscriptionRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.saveWebhookSubscription(
                        principal.getTenantId(),
                        principal.getUserId(),
                        subscriptionId,
                        request,
                        principal.getUsername(),
                        allowedReconViews(principal))));
    }

    @DeleteMapping("/webhook-subscriptions/{subscriptionId}")
    public ResponseEntity<ApiResponse<AlertsResponse>> deleteWebhookSubscription(
            @PathVariable UUID subscriptionId,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.deleteWebhookSubscription(
                        principal.getTenantId(),
                        principal.getUserId(),
                        principal.getUsername(),
                        subscriptionId,
                        allowedReconViews(principal))));
    }

    @PostMapping("/escalation-policies")
    public ResponseEntity<ApiResponse<AlertsResponse>> createEscalationPolicy(
            @RequestBody SaveAlertEscalationPolicyRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.saveEscalationPolicy(
                        principal.getTenantId(),
                        principal.getUserId(),
                        principal.getUsername(),
                        null,
                        request,
                        allowedReconViews(principal))));
    }

    @PutMapping("/escalation-policies/{policyId}")
    public ResponseEntity<ApiResponse<AlertsResponse>> updateEscalationPolicy(
            @PathVariable UUID policyId,
            @RequestBody SaveAlertEscalationPolicyRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.saveEscalationPolicy(
                        principal.getTenantId(),
                        principal.getUserId(),
                        principal.getUsername(),
                        policyId,
                        request,
                        allowedReconViews(principal))));
    }

    @DeleteMapping("/escalation-policies/{policyId}")
    public ResponseEntity<ApiResponse<AlertsResponse>> deleteEscalationPolicy(
            @PathVariable UUID policyId,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.deleteEscalationPolicy(
                        principal.getTenantId(),
                        principal.getUserId(),
                        principal.getUsername(),
                        policyId,
                        allowedReconViews(principal))));
    }

    @PostMapping("/personal-subscriptions")
    public ResponseEntity<ApiResponse<AlertsResponse>> createPersonalSubscription(
            @RequestBody SaveAlertUserSubscriptionRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireViewAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.savePersonalSubscription(
                        principal.getTenantId(),
                        principal.getUserId(),
                        principal.getUsername(),
                        null,
                        request,
                        allowedReconViews(principal))));
    }

    @PutMapping("/personal-subscriptions/{subscriptionId}")
    public ResponseEntity<ApiResponse<AlertsResponse>> updatePersonalSubscription(
            @PathVariable UUID subscriptionId,
            @RequestBody SaveAlertUserSubscriptionRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireViewAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.savePersonalSubscription(
                        principal.getTenantId(),
                        principal.getUserId(),
                        principal.getUsername(),
                        subscriptionId,
                        request,
                        allowedReconViews(principal))));
    }

    @DeleteMapping("/personal-subscriptions/{subscriptionId}")
    public ResponseEntity<ApiResponse<AlertsResponse>> deletePersonalSubscription(
            @PathVariable UUID subscriptionId,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireViewAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.deletePersonalSubscription(
                        principal.getTenantId(),
                        principal.getUserId(),
                        principal.getUsername(),
                        subscriptionId,
                        allowedReconViews(principal))));
    }

    @PostMapping("/digests")
    public ResponseEntity<ApiResponse<AlertsResponse>> createDigestSubscription(
            @RequestBody SaveAlertDigestSubscriptionRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.saveDigestSubscription(
                        principal.getTenantId(),
                        principal.getUserId(),
                        null,
                        request,
                        principal.getUsername(),
                        allowedReconViews(principal))));
    }

    @PutMapping("/digests/{subscriptionId}")
    public ResponseEntity<ApiResponse<AlertsResponse>> updateDigestSubscription(
            @PathVariable UUID subscriptionId,
            @RequestBody SaveAlertDigestSubscriptionRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.saveDigestSubscription(
                        principal.getTenantId(),
                        principal.getUserId(),
                        subscriptionId,
                        request,
                        principal.getUsername(),
                        allowedReconViews(principal))));
    }

    @DeleteMapping("/digests/{subscriptionId}")
    public ResponseEntity<ApiResponse<AlertsResponse>> deleteDigestSubscription(
            @PathVariable UUID subscriptionId,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.deleteDigestSubscription(
                        principal.getTenantId(),
                        principal.getUserId(),
                        principal.getUsername(),
                        subscriptionId,
                        allowedReconViews(principal))));
    }

    @PostMapping("/digests/run-now")
    public ResponseEntity<ApiResponse<AlertsResponse>> runDigestsNow(
            @RequestParam(name = "subscriptionId", required = false) UUID subscriptionId,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        alertDigestService.runDigestsForTenant(principal.getTenantId(), subscriptionId);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.getAlerts(principal.getTenantId(), principal.getUserId(), principal.getUsername(), null, allowedReconViews(principal))));
    }

    @PostMapping("/anomaly-rules")
    public ResponseEntity<ApiResponse<AlertsResponse>> createAnomalyRule(
            @RequestBody SaveAlertAnomalyRuleRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.saveAnomalyRule(
                        principal.getTenantId(),
                        principal.getUserId(),
                        null,
                        request,
                        principal.getUsername(),
                        allowedReconViews(principal))));
    }

    @PutMapping("/anomaly-rules/{ruleId}")
    public ResponseEntity<ApiResponse<AlertsResponse>> updateAnomalyRule(
            @PathVariable UUID ruleId,
            @RequestBody SaveAlertAnomalyRuleRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.saveAnomalyRule(
                        principal.getTenantId(),
                        principal.getUserId(),
                        ruleId,
                        request,
                        principal.getUsername(),
                        allowedReconViews(principal))));
    }

    @DeleteMapping("/anomaly-rules/{ruleId}")
    public ResponseEntity<ApiResponse<AlertsResponse>> deleteAnomalyRule(
            @PathVariable UUID ruleId,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.deleteAnomalyRule(
                        principal.getTenantId(),
                        principal.getUserId(),
                        principal.getUsername(),
                        ruleId,
                        allowedReconViews(principal))));
    }

    @PostMapping("/anomaly-rules/run-now")
    public ResponseEntity<ApiResponse<AlertsResponse>> runAnomalyDetectionNow(
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        alertAnomalyDetectionService.runAnomalyDetectionForTenant(principal.getTenantId());
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.getAlerts(principal.getTenantId(), principal.getUserId(), principal.getUsername(), null, allowedReconViews(principal))));
    }

    @PostMapping("/sms-subscriptions")
    public ResponseEntity<ApiResponse<AlertsResponse>> createSmsSubscription(
            @RequestBody SaveAlertSmsSubscriptionRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.saveSmsSubscription(
                        principal.getTenantId(),
                        principal.getUserId(),
                        null,
                        request,
                        principal.getUsername(),
                        allowedReconViews(principal))));
    }

    @PutMapping("/sms-subscriptions/{subscriptionId}")
    public ResponseEntity<ApiResponse<AlertsResponse>> updateSmsSubscription(
            @PathVariable UUID subscriptionId,
            @RequestBody SaveAlertSmsSubscriptionRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.saveSmsSubscription(
                        principal.getTenantId(),
                        principal.getUserId(),
                        subscriptionId,
                        request,
                        principal.getUsername(),
                        allowedReconViews(principal))));
    }

    @DeleteMapping("/sms-subscriptions/{subscriptionId}")
    public ResponseEntity<ApiResponse<AlertsResponse>> deleteSmsSubscription(
            @PathVariable UUID subscriptionId,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.deleteSmsSubscription(
                        principal.getTenantId(),
                        principal.getUserId(),
                        principal.getUsername(),
                        subscriptionId,
                        allowedReconViews(principal))));
    }

    private void requireViewAccess(ReconUserPrincipal principal) {
        if (!principal.hasPermission("ALERT_VIEW")) {
            throw new AccessDeniedException("Missing permission: ALERT_VIEW");
        }
    }

    private void requireEditAccess(ReconUserPrincipal principal) {
        requireViewAccess(principal);
        if (!principal.hasPermission("ALERT_EDIT")) {
            throw new AccessDeniedException("Missing permission: ALERT_EDIT");
        }
    }

    private Set<String> allowedReconViews(ReconUserPrincipal principal) {
        Set<String> allowed = new LinkedHashSet<>();
        if (principal.hasPermission("RECON_XSTORE_SIM")) {
            allowed.add("XSTORE_SIM");
        }
        if (principal.hasPermission("RECON_XSTORE_SIOCS")) {
            allowed.add("XSTORE_SIOCS");
        }
        if (principal.hasPermission("RECON_XSTORE_XOCS")) {
            allowed.add("XSTORE_XOCS");
        }
        if (principal.hasPermission("RECON_SIOCS_MFCS")) {
            allowed.add("SIOCS_MFCS");
        }
        return allowed;
    }

    private String normalizeReconView(String reconView) {
        return reconView == null || reconView.isBlank() ? null : reconView.toUpperCase();
    }
}
