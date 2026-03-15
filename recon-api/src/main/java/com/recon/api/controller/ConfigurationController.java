package com.recon.api.controller;

import com.recon.api.domain.ApiResponse;
import com.recon.api.domain.ConfigurationCatalogResponse;
import com.recon.api.domain.ConfigurationOverrideRequest;
import com.recon.api.security.ReconUserPrincipal;
import com.recon.api.service.ConfigurationCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/v1/configurations")
@RequiredArgsConstructor
public class ConfigurationController {

    private final ConfigurationCatalogService configurationCatalogService;

    @GetMapping("/catalog")
    public ResponseEntity<ApiResponse<ConfigurationCatalogResponse>> getCatalog(
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        boolean canViewModules = principal.hasPermission("CONFIG_MODULE_VIEW");
        boolean canViewSystem = principal.hasPermission("CONFIG_SYSTEM_VIEW");
        boolean canEdit = principal.hasPermission("CONFIG_EDIT");
        if (!canViewModules && !canViewSystem) {
            throw new AccessDeniedException("Missing configuration view permission");
        }
        return ResponseEntity.ok(ApiResponse.ok(
                configurationCatalogService.getCatalog(canViewModules, canViewSystem, canEdit)));
    }

    @PutMapping("/overrides/{configKey}")
    public ResponseEntity<ApiResponse<ConfigurationCatalogResponse>> saveOverride(
            @PathVariable String configKey,
            @RequestBody ConfigurationOverrideRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireAnyPermission(principal, Set.of("CONFIG_EDIT"));
        try {
            configurationCatalogService.saveOverride(configKey, request.getValue(), principal.getUserId());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage(), ex);
        }
        return ResponseEntity.ok(ApiResponse.ok(
                configurationCatalogService.getCatalog(
                        principal.hasPermission("CONFIG_MODULE_VIEW"),
                        principal.hasPermission("CONFIG_SYSTEM_VIEW"),
                        principal.hasPermission("CONFIG_EDIT"))));
    }

    @DeleteMapping("/overrides/{configKey}")
    public ResponseEntity<ApiResponse<ConfigurationCatalogResponse>> clearOverride(
            @PathVariable String configKey,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireAnyPermission(principal, Set.of("CONFIG_EDIT"));
        try {
            configurationCatalogService.clearOverride(configKey, principal.getUserId());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage(), ex);
        }
        return ResponseEntity.ok(ApiResponse.ok(
                configurationCatalogService.getCatalog(
                        principal.hasPermission("CONFIG_MODULE_VIEW"),
                        principal.hasPermission("CONFIG_SYSTEM_VIEW"),
                        principal.hasPermission("CONFIG_EDIT"))));
    }

    private void requireAnyPermission(ReconUserPrincipal principal, Set<String> permissions) {
        boolean allowed = permissions.stream().anyMatch(principal::hasPermission);
        if (!allowed) {
            throw new AccessDeniedException("Missing one of permissions: " + permissions);
        }
    }
}
