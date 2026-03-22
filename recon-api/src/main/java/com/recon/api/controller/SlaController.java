package com.recon.api.controller;

import com.recon.api.domain.ApiResponse;
import com.recon.api.domain.ExceptionSlaRuleDto;
import com.recon.api.domain.SaveExceptionSlaRuleRequest;
import com.recon.api.domain.SaveTenantOperatingModelRequest;
import com.recon.api.domain.SlaManagementResponse;
import com.recon.api.domain.TenantOperatingModelDto;
import com.recon.api.security.ReconUserPrincipal;
import com.recon.api.service.ExceptionSlaService;
import com.recon.api.service.TenantOperatingModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sla")
@RequiredArgsConstructor
@Slf4j
public class SlaController {

    private final ExceptionSlaService exceptionSlaService;
    private final TenantOperatingModelService tenantOperatingModelService;

    @GetMapping
    public ResponseEntity<ApiResponse<SlaManagementResponse>> getSlaManagement(
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireSlaAccess(principal, reconView, false);
            return ResponseEntity.ok(ApiResponse.ok(
                    exceptionSlaService.getManagementData(principal.getTenantId(), reconView)));
        } catch (Exception e) {
            log.error("Get SLA management error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/rules/{reconView}/{severity}")
    public ResponseEntity<ApiResponse<List<ExceptionSlaRuleDto>>> saveRule(
            @PathVariable("reconView") String reconView,
            @PathVariable("severity") String severity,
            @RequestBody SaveExceptionSlaRuleRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireSlaAccess(principal, reconView, true);
            return ResponseEntity.ok(ApiResponse.ok(
                    exceptionSlaService.saveRule(
                            principal.getTenantId(),
                            reconView,
                            severity,
                            request,
                            principal.getUsername())));
        } catch (Exception e) {
            log.error("Save SLA rule error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/operating-model")
    public ResponseEntity<ApiResponse<TenantOperatingModelDto>> saveOperatingModel(
            @RequestBody SaveTenantOperatingModelRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireSlaAccess(principal, null, true);
            return ResponseEntity.ok(ApiResponse.ok(
                    tenantOperatingModelService.saveOperatingModel(
                            principal.getTenantId(),
                            request)));
        } catch (Exception e) {
            log.error("Save SLA operating model error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    private void requireSlaAccess(ReconUserPrincipal principal,
                                  String reconView,
                                  boolean edit) {
        String permission = edit ? "SLA_EDIT" : "SLA_VIEW";
        if (!principal.hasPermission(permission)) {
            throw new AccessDeniedException("Missing permission: " + permission);
        }
        if (reconView == null || reconView.isBlank()) {
            return;
        }
        String requiredPermission = switch (reconView.toUpperCase()) {
            case "XSTORE_SIOCS" -> "RECON_XSTORE_SIOCS";
            case "XSTORE_XOCS" -> "RECON_XSTORE_XOCS";
            case "XSTORE_SIM" -> "RECON_XSTORE_SIM";
            default -> null;
        };
        if (requiredPermission != null && !principal.hasPermission(requiredPermission)) {
            throw new AccessDeniedException("Missing permission: " + requiredPermission);
        }
    }
}
