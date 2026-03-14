package com.recon.api.controller;

import com.recon.api.domain.*;
import com.recon.api.security.ReconUserPrincipal;
import com.recon.api.service.RoleService;
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

    // ── Users ─────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserDto>>> getUsers(
            @RequestParam(name = "tenantId", defaultValue = "tenant-india")
            String tenantId,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_USERS");
        return ResponseEntity.ok(ApiResponse.ok(
                userService.getAllUsers(tenantId)));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserDto>> createUser(
            @RequestBody CreateUserRequest req,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_USERS");
        return ResponseEntity.ok(ApiResponse.ok(
                userService.createUser(req)));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserDto>> updateUser(
            @PathVariable("id") UUID id,
            @RequestBody CreateUserRequest req,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_USERS");
        return ResponseEntity.ok(ApiResponse.ok(
                userService.updateUser(id, req)));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse<String>> deactivateUser(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_USERS");
        userService.deactivateUser(id);
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
                userService.activateUser(id)));
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) ResetPasswordRequest req,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_USERS");
        userService.resetPassword(id, req);
        return ResponseEntity.ok(
                ApiResponse.ok("Password reset successfully"));
    }

    @DeleteMapping("/users/{id}/permanent")
    public ResponseEntity<ApiResponse<String>> deleteUserPermanently(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_USERS");
        userService.deleteUser(id);
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
                userService.assignRoles(id, req)));
    }

    @PostMapping("/users/{id}/stores")
    public ResponseEntity<ApiResponse<UserDto>> assignStores(
            @PathVariable("id") UUID id,
            @RequestBody AssignStoresRequest req,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_USERS");
        return ResponseEntity.ok(ApiResponse.ok(
                userService.assignStores(id, req)));
    }

    // ── Roles ─────────────────────────────────────────────

    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<RoleDto>>> getRoles(
            @RequestParam(name = "tenantId", defaultValue = "tenant-india")
            String tenantId,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_ROLES");
        return ResponseEntity.ok(ApiResponse.ok(
                roleService.getAllRoles(tenantId)));
    }

    @PostMapping("/roles")
    public ResponseEntity<ApiResponse<RoleDto>> createRole(
            @RequestBody CreateRoleRequest req,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_ROLES");
        return ResponseEntity.ok(ApiResponse.ok(
                roleService.createRole(req)));
    }

    @PutMapping("/roles/{id}")
    public ResponseEntity<ApiResponse<RoleDto>> updateRole(
            @PathVariable("id") UUID id,
            @RequestBody CreateRoleRequest req,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_ROLES");
        return ResponseEntity.ok(ApiResponse.ok(
                roleService.updateRole(id, req)));
    }

    @PostMapping("/roles/{id}/permissions")
    public ResponseEntity<ApiResponse<RoleDto>> assignPermissions(
            @PathVariable("id") UUID id,
            @RequestBody Set<UUID> permissionIds,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_ROLES");
        return ResponseEntity.ok(ApiResponse.ok(
                roleService.assignPermissions(id, permissionIds)));
    }

    @DeleteMapping("/roles/{id}")
    public ResponseEntity<ApiResponse<String>> deleteRole(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        requirePermission(principal, "ADMIN_ROLES");
        roleService.deleteRole(id);
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

    // ── Helper ────────────────────────────────────────────

    private void requirePermission(
            ReconUserPrincipal principal, String permission) {
        if (!principal.hasPermission(permission)) {
            throw new AccessDeniedException(
                    "Missing permission: " + permission);
        }
    }
}