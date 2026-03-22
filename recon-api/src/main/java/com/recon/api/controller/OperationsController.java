package com.recon.api.controller;

import com.recon.api.domain.ApiResponse;
import com.recon.api.domain.OperationActionResponseDto;
import com.recon.api.domain.OperationsResponse;
import com.recon.api.domain.ReplayWindowOperationRequest;
import com.recon.api.domain.ResetCheckpointOperationRequest;
import com.recon.api.security.ReconUserPrincipal;
import com.recon.api.service.OperationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
@RequiredArgsConstructor
public class OperationsController {

    private final OperationsService operationsService;

    @GetMapping
    public ResponseEntity<ApiResponse<OperationsResponse>> getOperations(
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireView(principal);
        return ResponseEntity.ok(ApiResponse.ok(operationsService.getOperations(principal.getTenantId())));
    }

    @PostMapping("/{moduleId}/actions/{actionKey}")
    public ResponseEntity<ApiResponse<OperationActionResponseDto>> executeAction(
            @PathVariable String moduleId,
            @PathVariable String actionKey,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireSafeExecute(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                operationsService.executeSafeAction(
                        principal.getTenantId(),
                        moduleId,
                        actionKey,
                        principal.getUsername()
                )));
    }

    @PostMapping("/{moduleId}/actions/reset-checkpoint")
    public ResponseEntity<ApiResponse<OperationActionResponseDto>> resetCheckpoint(
            @PathVariable String moduleId,
            @RequestBody ResetCheckpointOperationRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireCheckpointReset(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                operationsService.resetCheckpoint(
                        principal.getTenantId(),
                        moduleId,
                        principal.getUsername(),
                        request
                )));
    }

    @PostMapping("/{moduleId}/actions/replay-window")
    public ResponseEntity<ApiResponse<OperationActionResponseDto>> replayWindow(
            @PathVariable String moduleId,
            @RequestBody ReplayWindowOperationRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireAdvancedExecute(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                operationsService.replayWindow(
                        principal.getTenantId(),
                        moduleId,
                        principal.getUsername(),
                        request
                )));
    }

    private void requireView(ReconUserPrincipal principal) {
        if (!principal.hasPermission("OPS_VIEW")) {
            throw new AccessDeniedException("Missing permission: OPS_VIEW");
        }
    }

    private void requireSafeExecute(ReconUserPrincipal principal) {
        requireView(principal);
        if (!principal.hasPermission("OPS_EXECUTE_SAFE")) {
            throw new AccessDeniedException("Missing permission: OPS_EXECUTE_SAFE");
        }
    }

    private void requireAdvancedExecute(ReconUserPrincipal principal) {
        requireView(principal);
        if (!principal.hasPermission("OPS_EXECUTE_ADVANCED")) {
            throw new AccessDeniedException("Missing permission: OPS_EXECUTE_ADVANCED");
        }
    }

    private void requireCheckpointReset(ReconUserPrincipal principal) {
        requireView(principal);
        if (!principal.hasPermission("OPS_CHECKPOINT_RESET")) {
            throw new AccessDeniedException("Missing permission: OPS_CHECKPOINT_RESET");
        }
    }
}
