package com.recon.api.controller;

import com.recon.api.domain.*;
import com.recon.api.security.ReconUserPrincipal;
import com.recon.api.service.AuthService;
import com.recon.api.service.OidcLoginService;
import com.recon.api.service.SamlLoginService;
import com.recon.api.service.SsoLoginCompletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final OidcLoginService oidcLoginService;
    private final SamlLoginService samlLoginService;
    private final SsoLoginCompletionService ssoLoginCompletionService;

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

    @PostMapping("/oidc/start")
    public ResponseEntity<ApiResponse<OidcLoginStartResponse>> startOidcLogin(
            @RequestBody OidcLoginStartRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    oidcLoginService.startLogin(request)));
        } catch (Exception e) {
            log.warn("OIDC login start failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/oidc/callback")
    public ResponseEntity<ApiResponse<LoginResponse>> completeOidcLogin(
            @RequestBody OidcLoginCallbackRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    oidcLoginService.completeLogin(request)));
        } catch (Exception e) {
            log.warn("OIDC login failed: {}", e.getMessage());
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/saml/start")
    public ResponseEntity<ApiResponse<SamlLoginStartResponse>> startSamlLogin(
            @RequestBody SamlLoginStartRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    samlLoginService.startLogin(request)));
        } catch (Exception e) {
            log.warn("SAML login start failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/sso/complete")
    public ResponseEntity<ApiResponse<LoginResponse>> completeSsoLogin(
            @RequestBody SsoCompletionRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    ssoLoginCompletionService.exchange(
                            request != null ? request.getCode() : null)));
        } catch (Exception e) {
            log.warn("SSO completion failed: {}", e.getMessage());
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/saml/acs/{tenantId}")
    public RedirectView completeSamlAcs(
            @PathVariable("tenantId") String tenantId,
            @RequestParam("SAMLResponse") String samlResponse,
            @RequestParam(value = "RelayState", required = false) String relayState) {
        RedirectView redirectView = new RedirectView();
        redirectView.setExposeModelAttributes(false);
        redirectView.setUrl(
                samlLoginService.consumeAssertion(
                        tenantId,
                        samlResponse,
                        relayState));
        return redirectView;
    }

    @GetMapping("/login-options")
    public ResponseEntity<ApiResponse<LoginOptionsResponse>> getLoginOptions(
            @RequestParam(name = "tenantId", defaultValue = "tenant-india") String tenantId) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    authService.getLoginOptions(tenantId)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
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
