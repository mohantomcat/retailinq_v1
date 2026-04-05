package com.recon.api.controller;

import com.recon.api.domain.ApiResponse;
import com.recon.api.domain.IntegrationHubResponse;
import com.recon.api.domain.IntegrationMessageTraceDetailDto;
import com.recon.api.domain.IntegrationMessageTraceItemDto;
import com.recon.api.domain.IntegrationReplayRequestDto;
import com.recon.api.domain.ResolveIntegrationErrorRequest;
import com.recon.api.domain.SaveIntegrationReplayRequest;
import com.recon.api.security.ReconUserPrincipal;
import com.recon.api.service.IntegrationHubService;
import com.recon.api.service.ReconModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integration-hub")
@RequiredArgsConstructor
public class IntegrationHubController {

    private final IntegrationHubService integrationHubService;
    private final ReconModuleService reconModuleService;

    @GetMapping
    public ResponseEntity<ApiResponse<IntegrationHubResponse>> getHub(
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireView(principal);
        reconModuleService.requireAccess(principal.getTenantId(), reconView, principal.getPermissions());
        return ResponseEntity.ok(ApiResponse.ok(
                integrationHubService.getHub(
                        principal.getTenantId(),
                        reconModuleService.allowedReconViews(principal.getTenantId(), principal.getPermissions()),
                        reconView)));
    }

    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<List<IntegrationMessageTraceItemDto>>> getMessages(
            @RequestParam(name = "reconView", required = false) String reconView,
            @RequestParam(required = false) String connectorKey,
            @RequestParam(required = false) String flowKey,
            @RequestParam(required = false) String businessKey,
            @RequestParam(required = false) String documentId,
            @RequestParam(required = false) String messageStatus,
            @RequestParam(defaultValue = "25") int limit,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireView(principal);
        reconModuleService.requireAccess(principal.getTenantId(), reconView, principal.getPermissions());
        return ResponseEntity.ok(ApiResponse.ok(
                integrationHubService.getMessages(
                        principal.getTenantId(),
                        reconModuleService.allowedReconViews(principal.getTenantId(), principal.getPermissions()),
                        reconView,
                        connectorKey,
                        flowKey,
                        businessKey,
                        documentId,
                        messageStatus,
                        limit
                )
        ));
    }

    @GetMapping("/messages/{id}")
    public ResponseEntity<ApiResponse<IntegrationMessageTraceDetailDto>> getMessageDetail(
            @PathVariable UUID id,
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireView(principal);
        reconModuleService.requireAccess(principal.getTenantId(), reconView, principal.getPermissions());
        return ResponseEntity.ok(ApiResponse.ok(
                integrationHubService.getMessageDetail(
                        principal.getTenantId(),
                        reconModuleService.allowedReconViews(principal.getTenantId(), principal.getPermissions()),
                        reconView,
                        id)));
    }

    @PostMapping("/replay-requests")
    public ResponseEntity<ApiResponse<IntegrationReplayRequestDto>> createReplayRequest(
            @RequestBody SaveIntegrationReplayRequest request,
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireReplay(principal);
        reconModuleService.requireAccess(principal.getTenantId(), reconView, principal.getPermissions());
        return ResponseEntity.ok(ApiResponse.ok(
                integrationHubService.createReplayRequest(
                        principal.getTenantId(),
                        principal.getUsername(),
                        reconModuleService.allowedReconViews(principal.getTenantId(), principal.getPermissions()),
                        reconView,
                        request)
        ));
    }

    @PostMapping("/replay-requests/{id}/execute")
    public ResponseEntity<ApiResponse<IntegrationReplayRequestDto>> executeReplayRequest(
            @PathVariable UUID id,
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireReplay(principal);
        reconModuleService.requireAccess(principal.getTenantId(), reconView, principal.getPermissions());
        return ResponseEntity.ok(ApiResponse.ok(
                integrationHubService.executeReplayRequest(
                        principal.getTenantId(),
                        principal.getUsername(),
                        reconModuleService.allowedReconViews(principal.getTenantId(), principal.getPermissions()),
                        reconView,
                        id)
        ));
    }

    @PostMapping("/errors/{id}/resolve")
    public ResponseEntity<ApiResponse<com.recon.api.domain.IntegrationErrorQueueItemDto>> resolveError(
            @PathVariable UUID id,
            @RequestBody(required = false) ResolveIntegrationErrorRequest request,
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireReplay(principal);
        reconModuleService.requireAccess(principal.getTenantId(), reconView, principal.getPermissions());
        return ResponseEntity.ok(ApiResponse.ok(
                integrationHubService.resolveError(
                        principal.getTenantId(),
                        principal.getUsername(),
                        reconModuleService.allowedReconViews(principal.getTenantId(), principal.getPermissions()),
                        reconView,
                        id,
                        request)
        ));
    }

    private void requireView(ReconUserPrincipal principal) {
        if (!principal.hasPermission("INTEGRATION_HUB_VIEW")) {
            throw new AccessDeniedException("Missing permission: INTEGRATION_HUB_VIEW");
        }
    }

    private void requireReplay(ReconUserPrincipal principal) {
        requireView(principal);
        if (!principal.hasPermission("INTEGRATION_HUB_REPLAY")) {
            throw new AccessDeniedException("Missing permission: INTEGRATION_HUB_REPLAY");
        }
    }
}
