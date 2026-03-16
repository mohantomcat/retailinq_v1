package com.recon.api.controller;

import com.recon.api.domain.AddExceptionCommentRequest;
import com.recon.api.domain.ApiResponse;
import com.recon.api.domain.ExceptionCaseDto;
import com.recon.api.domain.UpdateExceptionCaseRequest;
import com.recon.api.security.ReconUserPrincipal;
import com.recon.api.service.ExceptionWorkbenchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exceptions")
@RequiredArgsConstructor
@Slf4j
public class ExceptionController {

    private final ExceptionWorkbenchService service;

    @GetMapping("/cases/{transactionKey}")
    public ResponseEntity<ApiResponse<ExceptionCaseDto>> getCase(
            @PathVariable("transactionKey") String transactionKey,
            @RequestParam(name = "reconView") String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireCaseAccess(principal, reconView, false);
            return ResponseEntity.ok(ApiResponse.ok(
                    service.getCase(principal.getTenantId(), transactionKey, reconView)));
        } catch (Exception e) {
            log.error("Get exception case error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/cases/{transactionKey}")
    public ResponseEntity<ApiResponse<ExceptionCaseDto>> upsertCase(
            @PathVariable("transactionKey") String transactionKey,
            @RequestBody UpdateExceptionCaseRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireCaseAccess(principal, request != null ? request.getReconView() : null, true);
            return ResponseEntity.ok(ApiResponse.ok(
                    service.upsertCase(
                            principal.getTenantId(),
                            transactionKey,
                            request != null ? request.getReconView() : null,
                            request,
                            principal.getUsername())));
        } catch (Exception e) {
            log.error("Upsert exception case error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/cases/{transactionKey}/comments")
    public ResponseEntity<ApiResponse<ExceptionCaseDto>> addComment(
            @PathVariable("transactionKey") String transactionKey,
            @RequestBody AddExceptionCommentRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireCaseAccess(principal, request != null ? request.getReconView() : null, true);
            return ResponseEntity.ok(ApiResponse.ok(
                    service.addComment(
                            principal.getTenantId(),
                            transactionKey,
                            request != null ? request.getReconView() : null,
                            request,
                            principal.getUsername())));
        } catch (Exception e) {
            log.error("Add exception comment error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    private void requireCaseAccess(ReconUserPrincipal principal,
                                   String reconView,
                                   boolean edit) {
        String permission = edit ? "EXCEPTION_EDIT" : "EXCEPTION_VIEW";
        if (!principal.hasPermission(permission)) {
            throw new AccessDeniedException("Missing permission: " + permission);
        }
        if (!principal.hasPermission("RECON_VIEW")) {
            throw new AccessDeniedException("Missing permission: RECON_VIEW");
        }
        if (reconView == null || reconView.isBlank()) {
            throw new AccessDeniedException("reconView is required");
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
