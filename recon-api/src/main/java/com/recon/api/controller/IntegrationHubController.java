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

    @GetMapping
    public ResponseEntity<ApiResponse<IntegrationHubResponse>> getHub(
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireView(principal);
        return ResponseEntity.ok(ApiResponse.ok(integrationHubService.getHub(principal.getTenantId())));
    }

    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<List<IntegrationMessageTraceItemDto>>> getMessages(
            @RequestParam(required = false) String connectorKey,
            @RequestParam(required = false) String flowKey,
            @RequestParam(required = false) String businessKey,
            @RequestParam(required = false) String documentId,
            @RequestParam(required = false) String messageStatus,
            @RequestParam(defaultValue = "25") int limit,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireView(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                integrationHubService.getMessages(
                        principal.getTenantId(),
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
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireView(principal);
        return ResponseEntity.ok(ApiResponse.ok(integrationHubService.getMessageDetail(principal.getTenantId(), id)));
    }

    @PostMapping("/replay-requests")
    public ResponseEntity<ApiResponse<IntegrationReplayRequestDto>> createReplayRequest(
            @RequestBody SaveIntegrationReplayRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireReplay(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                integrationHubService.createReplayRequest(principal.getTenantId(), principal.getUsername(), request)
        ));
    }

    @PostMapping("/replay-requests/{id}/execute")
    public ResponseEntity<ApiResponse<IntegrationReplayRequestDto>> executeReplayRequest(
            @PathVariable UUID id,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireReplay(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                integrationHubService.executeReplayRequest(principal.getTenantId(), principal.getUsername(), id)
        ));
    }

    @PostMapping("/errors/{id}/resolve")
    public ResponseEntity<ApiResponse<com.recon.api.domain.IntegrationErrorQueueItemDto>> resolveError(
            @PathVariable UUID id,
            @RequestBody(required = false) ResolveIntegrationErrorRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        requireReplay(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                integrationHubService.resolveError(principal.getTenantId(), principal.getUsername(), id, request)
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
