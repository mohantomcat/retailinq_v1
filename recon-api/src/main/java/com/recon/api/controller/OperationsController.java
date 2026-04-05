package com.recon.api.controller;

import com.recon.api.domain.ApiResponse;
import com.recon.api.domain.OperationActionResponseDto;
import com.recon.api.domain.OperationsJobCenterResponse;
import com.recon.api.domain.OperationsResponse;
import com.recon.api.domain.ReconJobDefinitionDto;
import com.recon.api.domain.ReconJobRunDto;
import com.recon.api.domain.ReplayWindowOperationRequest;
import com.recon.api.domain.ResetCheckpointOperationRequest;
import com.recon.api.domain.SaveReconJobDefinitionRequest;
import com.recon.api.security.ReconUserPrincipal;
import com.recon.api.service.OperationsService;
import com.recon.api.service.ReconModuleService;
import com.recon.api.service.ReconJobService;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/operations")
@RequiredArgsConstructor
public class OperationsController {

    private final OperationsService operationsService;
    private final ReconJobService reconJobService;
    private final ReconModuleService reconModuleService;

    @GetMapping
    public ResponseEntity<ApiResponse<OperationsResponse>> getOperations(
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireView(principal);
        reconModuleService.requireAccess(principal.getTenantId(), reconView, principal.getPermissions());
        return ResponseEntity.ok(ApiResponse.ok(
                operationsService.getOperations(
                        principal.getTenantId(),
                        reconModuleService.allowedReconViews(principal.getTenantId(), principal.getPermissions()),
                        reconView)));
    }

    @GetMapping("/jobs-center")
    public ResponseEntity<ApiResponse<OperationsJobCenterResponse>> getJobsCenter(
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireView(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                reconJobService.getJobCenter(
                        principal.getTenantId(),
                        reconModuleService.allowedReconViews(principal.getTenantId(), principal.getPermissions()))));
    }

    @PostMapping("/jobs")
    public ResponseEntity<ApiResponse<ReconJobDefinitionDto>> createJob(
            @RequestBody SaveReconJobDefinitionRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireAdvancedExecute(principal);
        request.setId(null);
        reconModuleService.requireAccess(principal.getTenantId(), request != null ? request.getReconView() : null, principal.getPermissions());
        return ResponseEntity.ok(ApiResponse.ok(
                reconJobService.saveJobDefinition(
                        principal.getTenantId(),
                        principal.getUsername(),
                        request,
                        reconModuleService.allowedReconViews(principal.getTenantId(), principal.getPermissions()))
        ));
    }

    @PutMapping("/jobs/{jobId}")
    public ResponseEntity<ApiResponse<ReconJobDefinitionDto>> updateJob(
            @PathVariable java.util.UUID jobId,
            @RequestBody SaveReconJobDefinitionRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireAdvancedExecute(principal);
        request.setId(jobId);
        reconModuleService.requireAccess(principal.getTenantId(), request != null ? request.getReconView() : null, principal.getPermissions());
        return ResponseEntity.ok(ApiResponse.ok(
                reconJobService.saveJobDefinition(
                        principal.getTenantId(),
                        principal.getUsername(),
                        request,
                        reconModuleService.allowedReconViews(principal.getTenantId(), principal.getPermissions()))
        ));
    }

    @PostMapping("/jobs/{jobId}/run")
    public ResponseEntity<ApiResponse<ReconJobRunDto>> runJobNow(
            @PathVariable java.util.UUID jobId,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireAdvancedExecute(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                reconJobService.triggerManualRun(
                        principal.getTenantId(),
                        jobId,
                        principal.getUsername(),
                        reconModuleService.allowedReconViews(principal.getTenantId(), principal.getPermissions()))
        ));
    }

    @PostMapping("/{moduleId}/actions/{actionKey}")
    public ResponseEntity<ApiResponse<OperationActionResponseDto>> executeAction(
            @PathVariable String moduleId,
            @PathVariable String actionKey,
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireSafeExecute(principal);
        requireOperationsModuleAccess(principal, moduleId, reconView);
        return ResponseEntity.ok(ApiResponse.ok(
                operationsService.executeSafeAction(
                        principal.getTenantId(),
                        moduleId,
                        actionKey,
                        principal.getUsername(),
                        reconView
                )));
    }

    @PostMapping("/{moduleId}/actions/reset-checkpoint")
    public ResponseEntity<ApiResponse<OperationActionResponseDto>> resetCheckpoint(
            @PathVariable String moduleId,
            @RequestBody ResetCheckpointOperationRequest request,
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireCheckpointReset(principal);
        requireOperationsModuleAccess(principal, moduleId, reconView);
        return ResponseEntity.ok(ApiResponse.ok(
                operationsService.resetCheckpoint(
                        principal.getTenantId(),
                        moduleId,
                        principal.getUsername(),
                        reconView,
                        request
                )));
    }

    @PostMapping("/{moduleId}/actions/replay-window")
    public ResponseEntity<ApiResponse<OperationActionResponseDto>> replayWindow(
            @PathVariable String moduleId,
            @RequestBody ReplayWindowOperationRequest request,
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireAdvancedExecute(principal);
        requireOperationsModuleAccess(principal, moduleId, reconView);
        return ResponseEntity.ok(ApiResponse.ok(
                operationsService.replayWindow(
                        principal.getTenantId(),
                        moduleId,
                        principal.getUsername(),
                        reconView,
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

    private void requireOperationsModuleAccess(ReconUserPrincipal principal, String moduleId, String reconView) {
        if (!reconModuleService.hasAccessToOperationsModule(principal.getTenantId(), moduleId, reconView, principal.getPermissions())) {
            throw new AccessDeniedException("Unknown operations module: " + moduleId);
        }
    }
}
