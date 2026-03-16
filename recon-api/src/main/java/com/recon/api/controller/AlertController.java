package com.recon.api.controller;

import com.recon.api.domain.AlertsResponse;
import com.recon.api.domain.ApiResponse;
import com.recon.api.domain.SaveAlertRuleRequest;
import com.recon.api.domain.UpdateAlertEventRequest;
import com.recon.api.security.ReconUserPrincipal;
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

    @GetMapping
    public ResponseEntity<ApiResponse<AlertsResponse>> getAlerts(
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireViewAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.getAlerts(principal.getTenantId(), normalizeReconView(reconView), allowedReconViews(principal))));
    }

    @PostMapping("/rules")
    public ResponseEntity<ApiResponse<AlertsResponse>> createRule(
            @RequestBody SaveAlertRuleRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.saveRule(principal.getTenantId(), null, request, principal.getUsername(), allowedReconViews(principal))));
    }

    @PutMapping("/rules/{ruleId}")
    public ResponseEntity<ApiResponse<AlertsResponse>> updateRule(
            @PathVariable UUID ruleId,
            @RequestBody SaveAlertRuleRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.saveRule(principal.getTenantId(), ruleId, request, principal.getUsername(), allowedReconViews(principal))));
    }

    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<ApiResponse<AlertsResponse>> deleteRule(
            @PathVariable UUID ruleId,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireEditAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.deleteRule(principal.getTenantId(), ruleId, allowedReconViews(principal))));
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
                        eventId,
                        request,
                        principal.getUsername(),
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
        return allowed;
    }

    private String normalizeReconView(String reconView) {
        return reconView == null || reconView.isBlank() ? null : reconView.toUpperCase();
    }
}
