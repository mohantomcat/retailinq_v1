package com.recon.api.controller;

import com.recon.api.domain.*;
import com.recon.api.security.ReconUserPrincipal;
import com.recon.api.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @RequestBody LoginRequest request) {
        try {
            LoginResponse response =
                    authService.login(request);
            return ResponseEntity.ok(
                    ApiResponse.ok(response));
        } catch (Exception e) {
            log.warn("Login failed: {}", e.getMessage());
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @RequestHeader("X-Refresh-Token")
            String refreshToken) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    authService.refresh(refreshToken)));
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> me(
            @AuthenticationPrincipal
            ReconUserPrincipal principal) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    authService.getCurrentUser(
                            principal.getUserId())));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserDto>> updateProfile(
            @AuthenticationPrincipal
            ReconUserPrincipal principal,
            @RequestBody UpdateProfileRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    authService.updateProfile(
                            principal.getUserId(), request)));
        } catch (Exception e) {
            log.warn("Profile update failed for user {}: {}",
                    principal != null ? principal.getUserId() : "unknown",
                    e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(
            @AuthenticationPrincipal
            ReconUserPrincipal principal,
            @RequestBody ChangePasswordRequest request) {
        try {
            authService.changePassword(
                    principal.getUserId(), request);
            return ResponseEntity.ok(
                    ApiResponse.ok("Password updated successfully"));
        } catch (Exception e) {
            log.warn("Password change failed for user {}: {}",
                    principal != null ? principal.getUserId() : "unknown",
                    e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}