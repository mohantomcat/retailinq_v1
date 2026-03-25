package com.recon.api.service;

import com.recon.api.domain.IntegrationHubResponse;
import com.recon.api.domain.IntegrationMessageTraceDetailDto;
import com.recon.api.domain.IntegrationMessageTraceItemDto;
import com.recon.api.domain.IntegrationReplayRequestDto;
import com.recon.api.domain.ResolveIntegrationErrorRequest;
import com.recon.api.domain.ReplayWindowOperationRequest;
import com.recon.api.domain.SaveIntegrationReplayRequest;
import com.recon.api.repository.IntegrationHubQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IntegrationHubService {

    private final IntegrationHubQueryRepository repository;
    private final IntegrationHubCatalogService catalogService;
    private final OperationsService operationsService;
    private final AuditLedgerService auditLedgerService;

    @Transactional(readOnly = true)
    public IntegrationHubResponse getHub(String tenantId) {
        catalogService.ensureCatalog(tenantId);
        return IntegrationHubResponse.builder()
                .summary(repository.getSummary(tenantId))
                .connectors(repository.getConnectors(tenantId))
                .flows(repository.getFlows(tenantId))
                .recentRuns(repository.getRecentRuns(tenantId, 12))
                .openErrors(repository.getOpenErrors(tenantId, 12))
                .replayRequests(repository.getReplayRequests(tenantId, 12))
                .build();
    }

    @Transactional
    public IntegrationReplayRequestDto createReplayRequest(String tenantId,
                                                           String username,
                                                           SaveIntegrationReplayRequest request) {
        catalogService.ensureCatalog(tenantId);
        if (request.getConnectorKey() == null || request.getConnectorKey().isBlank()) {
            throw new IllegalArgumentException("Connector key is required");
        }
        if (request.getFlowKey() == null || request.getFlowKey().isBlank()) {
            throw new IllegalArgumentException("Flow key is required");
        }
        IntegrationReplayRequestDto response = repository.createReplayRequest(tenantId, username, request);
        java.util.LinkedHashMap<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("connectorKey", request.getConnectorKey());
        metadata.put("flowKey", request.getFlowKey());
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("INTEGRATION_HUB")
                .moduleKey("INTEGRATION_HUB")
                .entityType("INTEGRATION_REPLAY_REQUEST")
                .entityKey(response.getId().toString())
                .actionType("CREATE")
                .title("Integration replay requested")
                .summary(request.getReason())
                .actor(username)
                .status(response.getReplayStatus())
                .referenceKey(request.getBusinessKey() != null && !request.getBusinessKey().isBlank()
                        ? request.getBusinessKey()
                        : request.getDocumentId())
                .controlFamily("INTEGRATION")
                .evidenceTags(java.util.List.of("INTEGRATION_HUB", "REPLAY"))
                .afterState(request)
                .metadata(metadata)
                .build());
        return response;
    }

    @Transactional(readOnly = true)
    public List<IntegrationMessageTraceItemDto> getMessages(String tenantId,
                                                            String connectorKey,
                                                            String flowKey,
                                                            String businessKey,
                                                            String documentId,
                                                            String messageStatus,
                                                            int limit) {
        catalogService.ensureCatalog(tenantId);
        return repository.getMessages(
                tenantId,
                connectorKey,
                flowKey,
                businessKey,
                documentId,
                messageStatus,
                Math.min(Math.max(limit, 1), 100)
        );
    }

    @Transactional(readOnly = true)
    public IntegrationMessageTraceDetailDto getMessageDetail(String tenantId, UUID messageId) {
        catalogService.ensureCatalog(tenantId);
        IntegrationMessageTraceDetailDto detail = repository.findMessageDetail(tenantId, messageId);
        if (detail == null) {
            throw new IllegalArgumentException("Integration message not found");
        }
        return detail;
    }

    @Transactional
    public IntegrationReplayRequestDto executeReplayRequest(String tenantId,
                                                            String username,
                                                            UUID replayRequestId) {
        catalogService.ensureCatalog(tenantId);
        IntegrationHubQueryRepository.IntegrationReplayRequestRecord request =
                repository.findReplayRequestForExecution(tenantId, replayRequestId);
        if (request == null) {
            throw new IllegalArgumentException("Replay request not found");
        }
        if ("RUNNING".equalsIgnoreCase(request.replayStatus())) {
            throw new IllegalStateException("Replay request is already running");
        }
        if ("COMPLETED".equalsIgnoreCase(request.replayStatus())) {
            throw new IllegalStateException("Replay request is already completed");
        }

        String moduleId = resolveOperationsModule(request.connectorKey());
        if (moduleId == null) {
            return completeReplayRequest(
                    tenantId,
                    username,
                    request,
                    "UNSUPPORTED",
                    "Executable replay is only available for cloud connectors with replay-window support"
            );
        }
        if (request.requestedFrom() == null || request.requestedTo() == null) {
            return completeReplayRequest(
                    tenantId,
                    username,
                    request,
                    "UNSUPPORTED",
                    "Replay execution currently requires a requested time window"
            );
        }

        repository.markReplayRunning(tenantId, replayRequestId, username);
        ReplayWindowOperationRequest replayWindowRequest = ReplayWindowOperationRequest.builder()
                .replayMode("STAGED_REPUBLISH")
                .fromBusinessDate(request.requestedFrom().toLocalDateTime().toLocalDate().toString())
                .toBusinessDate(request.requestedTo().toLocalDateTime().toLocalDate().toString())
                .build();
        try {
            var response = operationsService.replayWindow(tenantId, moduleId, username, replayWindowRequest);
            return completeReplayRequest(tenantId, username, request, "COMPLETED", response.getMessage());
        } catch (RuntimeException ex) {
            return completeReplayRequest(
                    tenantId,
                    username,
                    request,
                    "FAILED",
                    ex.getMessage() == null ? "Replay execution failed" : ex.getMessage()
            );
        }
    }

    @Transactional
    public com.recon.api.domain.IntegrationErrorQueueItemDto resolveError(String tenantId,
                                                                          String username,
                                                                          UUID errorId,
                                                                          ResolveIntegrationErrorRequest request) {
        catalogService.ensureCatalog(tenantId);
        var resolved = repository.resolveError(
                tenantId,
                errorId,
                request == null ? null : request.getResolutionNotes()
        );
        if (resolved == null) {
            throw new IllegalArgumentException("Integration error not found");
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("connectorKey", resolved.getConnectorKey());
        metadata.put("flowKey", resolved.getFlowKey());
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("INTEGRATION_HUB")
                .moduleKey("INTEGRATION_HUB")
                .entityType("INTEGRATION_ERROR")
                .entityKey(resolved.getId().toString())
                .actionType("RESOLVE")
                .title("Integration error resolved")
                .summary(resolved.getResolutionNotes())
                .actor(username)
                .status(resolved.getErrorStatus())
                .referenceKey(resolved.getBusinessKey() != null && !resolved.getBusinessKey().isBlank()
                        ? resolved.getBusinessKey()
                        : resolved.getDocumentId())
                .controlFamily("INTEGRATION")
                .evidenceTags(List.of("INTEGRATION_HUB", "ERROR_QUEUE", "RESOLVED"))
                .afterState(resolved)
                .metadata(metadata)
                .build());
        return resolved;
    }

    private IntegrationReplayRequestDto completeReplayRequest(String tenantId,
                                                              String username,
                                                              IntegrationHubQueryRepository.IntegrationReplayRequestRecord request,
                                                              String status,
                                                              String resolutionMessage) {
        IntegrationReplayRequestDto response = repository.completeReplayRequest(
                tenantId,
                request.id(),
                username,
                status,
                resolutionMessage
        );
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("connectorKey", request.connectorKey());
        metadata.put("flowKey", request.flowKey());
        metadata.put("replayStatus", status);
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("INTEGRATION_HUB")
                .moduleKey("INTEGRATION_HUB")
                .entityType("INTEGRATION_REPLAY_REQUEST")
                .entityKey(request.id().toString())
                .actionType("EXECUTE")
                .title("Integration replay execution updated")
                .summary(resolutionMessage)
                .actor(username)
                .status(status)
                .referenceKey(request.businessKey() != null && !request.businessKey().isBlank()
                        ? request.businessKey()
                        : request.documentId())
                .controlFamily("INTEGRATION")
                .evidenceTags(List.of("INTEGRATION_HUB", "REPLAY", status))
                .afterState(response)
                .metadata(metadata)
                .build());
        return response;
    }

    private String resolveOperationsModule(String connectorKey) {
        if (connectorKey == null || connectorKey.isBlank()) {
            return null;
        }
        return switch (connectorKey) {
            case "siocs-cloud-main" -> "siocs-cloud-connector";
            case "mfcs-rds-main" -> "mfcs-rds-connector";
            case "xocs-cloud-main" -> "xocs-cloud-connector";
            default -> null;
        };
    }
}
