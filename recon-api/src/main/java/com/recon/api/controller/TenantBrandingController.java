package com.recon.api.controller;

import com.recon.api.domain.ApiResponse;
import com.recon.api.domain.SaveTenantBrandingRequest;
import com.recon.api.domain.TenantBrandingDto;
import com.recon.api.security.ReconUserPrincipal;
import com.recon.api.service.TenantBrandingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class TenantBrandingController {

    private final TenantBrandingService tenantBrandingService;

    @GetMapping("/tenant-branding/current")
    public ResponseEntity<ApiResponse<TenantBrandingDto>> getCurrentBranding(
            @RequestParam(name = "tenantId", defaultValue = "tenant-india") String tenantId) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    tenantBrandingService.getCurrentBranding(tenantId)));
        } catch (Exception e) {
            log.error("Get current branding error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/admin/tenant-branding")
    public ResponseEntity<ApiResponse<TenantBrandingDto>> getAdminBranding(
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requirePermission(principal, "BRANDING_MANAGE");
            return ResponseEntity.ok(ApiResponse.ok(
                    tenantBrandingService.getCurrentBranding(principal.getTenantId())));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get admin branding error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/admin/tenant-branding")
    public ResponseEntity<ApiResponse<TenantBrandingDto>> saveAdminBranding(
            @RequestBody SaveTenantBrandingRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requirePermission(principal, "BRANDING_MANAGE");
            return ResponseEntity.ok(ApiResponse.ok(
                    tenantBrandingService.saveBranding(
                            principal.getTenantId(),
                            request,
                            principal.getUsername())));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Save admin branding error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    private void requirePermission(ReconUserPrincipal principal, String permission) {
        if (!principal.hasPermission(permission)) {
            throw new AccessDeniedException("Missing permission: " + permission);
        }
    }
}
