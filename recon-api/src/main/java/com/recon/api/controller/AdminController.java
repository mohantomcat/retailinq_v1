package com.recon.api.controller;

import com.recon.api.domain.*;
import com.recon.api.security.ReconUserPrincipal;
import com.recon.api.service.RoleService;
import com.recon.api.service.TenantAccessAdministrationService;
import com.recon.api.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final RoleService roleService;
    private final TenantAccessAdministrationService tenantAccessAdministrationService;

    // ── Users ─────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserDto>>> getUsers(
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_USERS");
        return ResponseEntity.ok(ApiResponse.ok(
                userService.getAllUsers(principal.getTenantId())));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserDto>> createUser(
            @RequestBody CreateUserRequest req,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_USERS");
        return ResponseEntity.ok(ApiResponse.ok(
                userService.createUser(principal.getTenantId(), req, principal.getUsername())));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserDto>> updateUser(
            @PathVariable("id") UUID id,
            @RequestBody CreateUserRequest req,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_USERS");
        return ResponseEntity.ok(ApiResponse.ok(
                userService.updateUser(principal.getTenantId(), id, req, principal.getUsername())));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse<String>> deactivateUser(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_USERS");
        userService.deactivateUser(principal.getTenantId(), id, principal.getUsername());
        return ResponseEntity.ok(
                ApiResponse.ok("User deactivated"));
    }

    @PostMapping("/users/{id}/activate")
    public ResponseEntity<ApiResponse<UserDto>> activateUser(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_USERS");
        return ResponseEntity.ok(ApiResponse.ok(
                userService.activateUser(principal.getTenantId(), id, principal.getUsername())));
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) ResetPasswordRequest req,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_USERS");
        userService.resetPassword(principal.getTenantId(), id, req, principal.getUsername());
        return ResponseEntity.ok(
                ApiResponse.ok("Password reset successfully"));
    }

    @DeleteMapping("/users/{id}/permanent")
    public ResponseEntity<ApiResponse<String>> deleteUserPermanently(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_USERS");
        userService.deleteUser(principal.getTenantId(), id, principal.getUsername());
        return ResponseEntity.ok(
                ApiResponse.ok("User deleted permanently"));
    }

    @PostMapping("/users/{id}/roles")
    public ResponseEntity<ApiResponse<UserDto>> assignRoles(
            @PathVariable("id") UUID id,
            @RequestBody AssignRolesRequest req,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_USERS");
        return ResponseEntity.ok(ApiResponse.ok(
                userService.assignRoles(principal.getTenantId(), id, req, principal.getUsername())));
    }

    @PostMapping("/users/{id}/stores")
    public ResponseEntity<ApiResponse<UserDto>> assignStores(
            @PathVariable("id") UUID id,
            @RequestBody AssignStoresRequest req,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_USERS");
        return ResponseEntity.ok(ApiResponse.ok(
                userService.assignStores(principal.getTenantId(), id, req, principal.getUsername())));
    }

    @PostMapping("/users/{id}/access-review")
    public ResponseEntity<ApiResponse<UserDto>> reviewUserAccess(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) ReviewUserAccessRequest req,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ACCESS_REVIEW_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
                userService.reviewUserAccess(principal.getTenantId(), id, req, principal.getUsername())));
    }

    @PostMapping("/users/{id}/org-scopes")
    public ResponseEntity<ApiResponse<List<UserOrganizationScopeDto>>> assignOrganizationScopes(
            @PathVariable("id") UUID id,
            @RequestBody AssignUserOrganizationScopesRequest req,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_ORG");
        return ResponseEntity.ok(ApiResponse.ok(
                tenantAccessAdministrationService.assignUserOrganizationScopes(
                        principal.getTenantId(),
                        id,
                        req,
                        principal.getUsername())));
    }

    // ── Roles ─────────────────────────────────────────────

    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<RoleDto>>> getRoles(
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_ROLES");
        return ResponseEntity.ok(ApiResponse.ok(
                roleService.getAllRoles(principal.getTenantId())));
    }

    @PostMapping("/roles")
    public ResponseEntity<ApiResponse<RoleDto>> createRole(
            @RequestBody CreateRoleRequest req,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_ROLES");
        return ResponseEntity.ok(ApiResponse.ok(
                roleService.createRole(principal.getTenantId(), req, principal.getUsername())));
    }

    @PutMapping("/roles/{id}")
    public ResponseEntity<ApiResponse<RoleDto>> updateRole(
            @PathVariable("id") UUID id,
            @RequestBody CreateRoleRequest req,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_ROLES");
        return ResponseEntity.ok(ApiResponse.ok(
                roleService.updateRole(principal.getTenantId(), id, req, principal.getUsername())));
    }

    @PostMapping("/roles/{id}/permissions")
    public ResponseEntity<ApiResponse<RoleDto>> assignPermissions(
            @PathVariable("id") UUID id,
            @RequestBody Set<UUID> permissionIds,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_ROLES");
        return ResponseEntity.ok(ApiResponse.ok(
                roleService.assignPermissions(principal.getTenantId(), id, permissionIds, principal.getUsername())));
    }

    @DeleteMapping("/roles/{id}")
    public ResponseEntity<ApiResponse<String>> deleteRole(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_ROLES");
        roleService.deleteRole(principal.getTenantId(), id, principal.getUsername());
        return ResponseEntity.ok(
                ApiResponse.ok("Role deleted successfully"));
    }

    // ── Permissions ───────────────────────────────────────

    @GetMapping("/permissions")
    public ResponseEntity<ApiResponse<List<Permission>>> getPermissions(
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_PERMISSIONS");
        return ResponseEntity.ok(ApiResponse.ok(
                roleService.getAllPermissions()));
    }

    @GetMapping("/org-units")
    public ResponseEntity<ApiResponse<List<OrganizationUnitDto>>> getOrganizationUnits(
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_ORG");
        return ResponseEntity.ok(ApiResponse.ok(
                tenantAccessAdministrationService.getOrganizationUnits(principal.getTenantId())));
    }

    @PostMapping("/org-units")
    public ResponseEntity<ApiResponse<OrganizationUnitDto>> createOrganizationUnit(
            @RequestBody SaveOrganizationUnitRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_ORG");
        return ResponseEntity.ok(ApiResponse.ok(
                tenantAccessAdministrationService.saveOrganizationUnit(
                        principal.getTenantId(),
                        null,
                        request,
                        principal.getUsername())));
    }

    @PutMapping("/org-units/{id}")
    public ResponseEntity<ApiResponse<OrganizationUnitDto>> updateOrganizationUnit(
            @PathVariable("id") UUID id,
            @RequestBody SaveOrganizationUnitRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_ORG");
        return ResponseEntity.ok(ApiResponse.ok(
                tenantAccessAdministrationService.saveOrganizationUnit(
                        principal.getTenantId(),
                        id,
                        request,
                        principal.getUsername())));
    }

    @GetMapping("/tenant-access-center")
    public ResponseEntity<ApiResponse<TenantAccessCenterResponse>> getTenantAccessCenter(
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requirePermission(principal, "TENANT_ACCESS_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
                tenantAccessAdministrationService.getAccessCenter(principal.getTenantId())));
    }

    @PutMapping("/tenant-auth-config")
    public ResponseEntity<ApiResponse<TenantAuthConfigDto>> saveTenantAuthConfig(
            @RequestBody SaveTenantAuthConfigRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requirePermission(principal, "TENANT_ACCESS_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
                tenantAccessAdministrationService.saveTenantAuthConfig(
                        principal.getTenantId(),
                        request,
                        principal.getUsername())));
    }

    @PutMapping("/recon-group-selections")
    public ResponseEntity<ApiResponse<List<ReconGroupSelectionDto>>> saveTenantReconGroupSelections(
            @RequestBody SaveTenantReconGroupSelectionsRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requirePermission(principal, "TENANT_ACCESS_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
                tenantAccessAdministrationService.saveTenantReconGroupSelections(
                        principal.getTenantId(),
                        request,
                        principal.getUsername())));
    }

    @PutMapping("/oidc-group-role-mappings")
    public ResponseEntity<ApiResponse<List<OidcGroupRoleMappingDto>>> saveOidcGroupRoleMappings(
            @RequestBody SaveOidcGroupRoleMappingsRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requirePermission(principal, "TENANT_ACCESS_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
                tenantAccessAdministrationService.saveOidcGroupRoleMappings(
                        principal.getTenantId(),
                        request,
                        principal.getUsername())));
    }

    @PutMapping("/identity-group-role-mappings")
    public ResponseEntity<ApiResponse<List<OidcGroupRoleMappingDto>>> saveIdentityGroupRoleMappings(
            @RequestBody SaveOidcGroupRoleMappingsRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requirePermission(principal, "TENANT_ACCESS_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
                tenantAccessAdministrationService.saveOidcGroupRoleMappings(
                        principal.getTenantId(),
                        request,
                        principal.getUsername())));
    }

    @PutMapping("/system-endpoint-profiles")
    public ResponseEntity<ApiResponse<List<SystemEndpointProfileDto>>> saveTenantSystemEndpointProfiles(
            @RequestBody SaveTenantSystemEndpointProfilesRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requirePermission(principal, "TENANT_ACCESS_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
                tenantAccessAdministrationService.saveTenantSystemEndpointProfiles(
                        principal.getTenantId(),
                        request,
                        principal.getUsername())));
    }

    @PostMapping("/api-keys")
    public ResponseEntity<ApiResponse<CreatedTenantApiKeyResponse>> createTenantApiKey(
            @RequestBody CreateTenantApiKeyRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requirePermission(principal, "API_ACCESS_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
                tenantAccessAdministrationService.createTenantApiKey(
                        principal.getTenantId(),
                        request,
                        principal.getUsername())));
    }

    @PostMapping("/api-keys/{id}/deactivate")
    public ResponseEntity<ApiResponse<TenantApiKeyDto>> deactivateTenantApiKey(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requirePermission(principal, "API_ACCESS_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
                tenantAccessAdministrationService.deactivateTenantApiKey(
                        principal.getTenantId(),
                        id,
                        principal.getUsername())));
    }

    // ── Helper ────────────────────────────────────────────

    private void requirePermission(
            ReconUserPrincipal principal, String permission) {
        if (!principal.hasPermission(permission)) {
            throw new AccessDeniedException(
                    "Missing permission: " + permission);
        }
    }
}
