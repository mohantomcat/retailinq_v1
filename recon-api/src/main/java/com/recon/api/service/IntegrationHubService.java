package com.recon.api.service;

import com.recon.api.domain.IntegrationHubResponse;
import com.recon.api.domain.IntegrationMessageTraceDetailDto;
import com.recon.api.domain.IntegrationMessageTraceItemDto;
import com.recon.api.domain.IntegrationReplayRequestDto;
import com.recon.api.domain.IntegrationConnectorDto;
import com.recon.api.domain.IntegrationErrorQueueItemDto;
import com.recon.api.domain.IntegrationFlowDto;
import com.recon.api.domain.IntegrationRunDto;
import com.recon.api.domain.ReconModuleDto;
import com.recon.api.domain.ResolveIntegrationErrorRequest;
import com.recon.api.domain.ReplayWindowOperationRequest;
import com.recon.api.domain.SaveIntegrationReplayRequest;
import com.recon.api.repository.IntegrationHubQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IntegrationHubService {

    private final IntegrationHubQueryRepository repository;
    private final IntegrationHubCatalogService catalogService;
    private final OperationsService operationsService;
    private final AuditLedgerService auditLedgerService;
    private final PlatformTransactionManager transactionManager;
    private final ReconModuleService reconModuleService;
    private final SystemEndpointProfileService systemEndpointProfileService;

    @Transactional(readOnly = true)
    public IntegrationHubResponse getHub(String tenantId, Collection<String> allowedReconViews) {
        return getHub(tenantId, allowedReconViews, null);
    }

    @Transactional(readOnly = true)
    public IntegrationHubResponse getHub(String tenantId,
                                         Collection<String> allowedReconViews,
                                         String reconView) {
        catalogService.ensureCatalog(tenantId);
        java.util.Set<String> normalizedAllowedViews = normalizeAllowedReconViews(allowedReconViews);
        java.util.Set<String> scopedAllowedViews = scopeReconViews(normalizedAllowedViews, reconView);
        java.util.Set<String> allowedConnectorKeys = reconModuleService.integrationConnectorKeysForAllowedViews(scopedAllowedViews);
        Map<String, List<ReconModuleDto>> impactedModulesByConnectorKey = impactedModulesByConnectorKey(normalizedAllowedViews);
        List<IntegrationConnectorDto> connectors = repository.getConnectors(tenantId).stream()
                .filter(connector -> allowedConnectorKeys.contains(connector.getConnectorKey()))
                .map(connector -> annotateConnector(tenantId, connector, impactedModulesByConnectorKey))
                .toList();
        Map<String, IntegrationConnectorDto> connectorByKey = connectors.stream()
                .collect(Collectors.toMap(
                        IntegrationConnectorDto::getConnectorKey,
                        connector -> connector,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return IntegrationHubResponse.builder()
                .summary(repository.getSummary(tenantId, allowedConnectorKeys))
                .connectors(connectors)
                .flows(repository.getFlows(tenantId).stream()
                        .filter(flow -> allowedConnectorKeys.contains(flow.getConnectorKey()))
                        .map(flow -> annotateFlow(tenantId, flow, connectorByKey, impactedModulesByConnectorKey))
                        .toList())
                .recentRuns(repository.getRecentRuns(tenantId, 12).stream()
                        .filter(run -> allowedConnectorKeys.contains(run.getConnectorKey()))
                        .map(run -> annotateRun(tenantId, run, connectorByKey, impactedModulesByConnectorKey))
                        .toList())
                .openErrors(repository.getOpenErrors(tenantId, 12).stream()
                        .filter(error -> allowedConnectorKeys.contains(error.getConnectorKey()))
                        .map(error -> annotateError(tenantId, error, connectorByKey, impactedModulesByConnectorKey))
                        .toList())
                .replayRequests(repository.getReplayRequests(tenantId, 12).stream()
                        .filter(request -> allowedConnectorKeys.contains(request.getConnectorKey()))
                        .map(request -> annotateReplayRequest(tenantId, request, connectorByKey, impactedModulesByConnectorKey))
                        .toList())
                .build();
    }

    @Transactional
    public IntegrationReplayRequestDto createReplayRequest(String tenantId,
                                                           String username,
                                                           Collection<String> allowedReconViews,
                                                           String reconView,
                                                           SaveIntegrationReplayRequest request) {
        catalogService.ensureCatalog(tenantId);
        java.util.Set<String> normalizedAllowedViews = normalizeAllowedReconViews(allowedReconViews);
        java.util.Set<String> scopedAllowedViews = scopeReconViews(normalizedAllowedViews, reconView);
        if (request.getConnectorKey() == null || request.getConnectorKey().isBlank()) {
            throw new IllegalArgumentException("Connector key is required");
        }
        if (!reconModuleService.isIntegrationConnectorAllowed(request.getConnectorKey(), scopedAllowedViews)) {
            throw new IllegalArgumentException("Missing access to the selected connector");
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
        return annotateReplayRequest(tenantId, response, Map.of(), impactedModulesByConnectorKey(normalizedAllowedViews));
    }

    @Transactional(readOnly = true)
    public List<IntegrationMessageTraceItemDto> getMessages(String tenantId,
                                                            Collection<String> allowedReconViews,
                                                            String reconView,
                                                            String connectorKey,
                                                            String flowKey,
                                                            String businessKey,
                                                            String documentId,
                                                            String messageStatus,
                                                            int limit) {
        catalogService.ensureCatalog(tenantId);
        java.util.Set<String> normalizedAllowedViews = normalizeAllowedReconViews(allowedReconViews);
        java.util.Set<String> scopedAllowedViews = scopeReconViews(normalizedAllowedViews, reconView);
        java.util.Set<String> allowedConnectorKeys = reconModuleService.integrationConnectorKeysForAllowedViews(scopedAllowedViews);
        return repository.getMessages(
                tenantId,
                connectorKey,
                flowKey,
                businessKey,
                documentId,
                messageStatus,
                Math.min(Math.max(limit, 1), 100)
        ).stream()
                .filter(message -> allowedConnectorKeys.contains(message.getConnectorKey()))
                .toList();
    }

    @Transactional(readOnly = true)
    public IntegrationMessageTraceDetailDto getMessageDetail(String tenantId,
                                                             Collection<String> allowedReconViews,
                                                             String reconView,
                                                             UUID messageId) {
        catalogService.ensureCatalog(tenantId);
        java.util.Set<String> normalizedAllowedViews = normalizeAllowedReconViews(allowedReconViews);
        java.util.Set<String> scopedAllowedViews = scopeReconViews(normalizedAllowedViews, reconView);
        IntegrationMessageTraceDetailDto detail = repository.findMessageDetail(tenantId, messageId);
        if (detail == null) {
            throw new IllegalArgumentException("Integration message not found");
        }
        if (!reconModuleService.isIntegrationConnectorAllowed(detail.getConnectorKey(), scopedAllowedViews)) {
            throw new IllegalArgumentException("Integration message not found");
        }
        return detail;
    }

    public IntegrationReplayRequestDto executeReplayRequest(String tenantId,
                                                            String username,
                                                            Collection<String> allowedReconViews,
                                                            String reconView,
                                                            UUID replayRequestId) {
        catalogService.ensureCatalog(tenantId);
        java.util.Set<String> normalizedAllowedViews = normalizeAllowedReconViews(allowedReconViews);
        java.util.Set<String> scopedAllowedViews = scopeReconViews(normalizedAllowedViews, reconView);
        IntegrationHubQueryRepository.IntegrationReplayRequestRecord request =
                repository.findReplayRequestForExecution(tenantId, replayRequestId);
        if (request == null) {
            throw new IllegalArgumentException("Replay request not found");
        }
        if (!reconModuleService.isIntegrationConnectorAllowed(request.connectorKey(), scopedAllowedViews)) {
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

        runInRequiresNewTransaction(() -> repository.markReplayRunning(tenantId, replayRequestId, username));
        ReplayWindowOperationRequest replayWindowRequest = ReplayWindowOperationRequest.builder()
                .replayMode("STAGED_REPUBLISH")
                .fromBusinessDate(request.requestedFrom().toLocalDateTime().toLocalDate().toString())
                .toBusinessDate(request.requestedTo().toLocalDateTime().toLocalDate().toString())
                .build();
        try {
            var response = operationsService.replayWindow(
                    tenantId,
                    moduleId,
                    username,
                    resolveReplayReconView(reconView, scopedAllowedViews, request.connectorKey()),
                    replayWindowRequest);
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
                                                                          Collection<String> allowedReconViews,
                                                                          String reconView,
                                                                          UUID errorId,
                                                                          ResolveIntegrationErrorRequest request) {
        catalogService.ensureCatalog(tenantId);
        java.util.Set<String> normalizedAllowedViews = normalizeAllowedReconViews(allowedReconViews);
        java.util.Set<String> scopedAllowedViews = scopeReconViews(normalizedAllowedViews, reconView);
        var existing = repository.findErrorById(tenantId, errorId);
        if (existing == null) {
            throw new IllegalArgumentException("Integration error not found");
        }
        if (!reconModuleService.isIntegrationConnectorAllowed(existing.getConnectorKey(), scopedAllowedViews)) {
            throw new IllegalArgumentException("Integration error not found");
        }
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
        return annotateError(tenantId, resolved, Map.of(), impactedModulesByConnectorKey(normalizedAllowedViews));
    }

    private IntegrationReplayRequestDto completeReplayRequest(String tenantId,
                                                              String username,
                                                              IntegrationHubQueryRepository.IntegrationReplayRequestRecord request,
                                                              String status,
                                                              String resolutionMessage) {
        return executeInRequiresNewTransaction(() -> {
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
        });
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

    private IntegrationConnectorDto annotateConnector(String tenantId,
                                                      IntegrationConnectorDto connector,
                                                      Map<String, List<ReconModuleDto>> impactedModulesByConnectorKey) {
        List<ReconModuleDto> affectedModules = dedupeModules(
                impactedModulesByConnectorKey.getOrDefault(normalizeConnectorKey(connector.getConnectorKey()), List.of()));
        List<String> affectedReconViews = affectedModules.stream().map(ReconModuleDto::getReconView).toList();
        List<String> affectedReconLabels = affectedModules.stream().map(ReconModuleDto::getLabel).filter(Objects::nonNull).toList();
        String endpointMode = resolveEndpointMode(tenantId, connector.getSourceSystem());
        return connector.toBuilder()
                .endpointMode(endpointMode)
                .sharedAsset(affectedReconViews.size() > 1)
                .affectedReconViews(affectedReconViews)
                .affectedReconLabels(affectedReconLabels)
                .build();
    }

    private IntegrationFlowDto annotateFlow(String tenantId,
                                            IntegrationFlowDto flow,
                                            Map<String, IntegrationConnectorDto> connectorByKey,
                                            Map<String, List<ReconModuleDto>> impactedModulesByConnectorKey) {
        IntegrationConnectorDto connector = connectorByKey.get(flow.getConnectorKey());
        List<ReconModuleDto> affectedModules = connector != null
                ? List.of()
                : dedupeModules(impactedModulesByConnectorKey.getOrDefault(normalizeConnectorKey(flow.getConnectorKey()), List.of()));
        List<String> affectedReconViews = connector != null ? connector.getAffectedReconViews() : affectedModules.stream().map(ReconModuleDto::getReconView).toList();
        List<String> affectedReconLabels = connector != null ? connector.getAffectedReconLabels() : affectedModules.stream().map(ReconModuleDto::getLabel).filter(Objects::nonNull).toList();
        String endpointMode = connector != null ? connector.getEndpointMode() : resolveEndpointMode(tenantId, flow.getSourceSystem());
        return flow.toBuilder()
                .endpointMode(endpointMode)
                .sharedAsset(affectedReconViews != null && affectedReconViews.size() > 1)
                .affectedReconViews(affectedReconViews == null ? List.of() : affectedReconViews)
                .affectedReconLabels(affectedReconLabels == null ? List.of() : affectedReconLabels)
                .build();
    }

    private IntegrationRunDto annotateRun(String tenantId,
                                          IntegrationRunDto run,
                                          Map<String, IntegrationConnectorDto> connectorByKey,
                                          Map<String, List<ReconModuleDto>> impactedModulesByConnectorKey) {
        IntegrationConnectorDto connector = connectorByKey.get(run.getConnectorKey());
        List<ReconModuleDto> affectedModules = connector != null
                ? List.of()
                : dedupeModules(impactedModulesByConnectorKey.getOrDefault(normalizeConnectorKey(run.getConnectorKey()), List.of()));
        List<String> affectedReconViews = connector != null ? connector.getAffectedReconViews() : affectedModules.stream().map(ReconModuleDto::getReconView).toList();
        List<String> affectedReconLabels = connector != null ? connector.getAffectedReconLabels() : affectedModules.stream().map(ReconModuleDto::getLabel).filter(Objects::nonNull).toList();
        String endpointMode = connector != null ? connector.getEndpointMode() : resolveEndpointMode(tenantId, run.getSourceSystem());
        return run.toBuilder()
                .endpointMode(endpointMode)
                .sharedAsset(affectedReconViews != null && affectedReconViews.size() > 1)
                .affectedReconViews(affectedReconViews == null ? List.of() : affectedReconViews)
                .affectedReconLabels(affectedReconLabels == null ? List.of() : affectedReconLabels)
                .build();
    }

    private IntegrationErrorQueueItemDto annotateError(String tenantId,
                                                       IntegrationErrorQueueItemDto error,
                                                       Map<String, IntegrationConnectorDto> connectorByKey,
                                                       Map<String, List<ReconModuleDto>> impactedModulesByConnectorKey) {
        IntegrationConnectorDto connector = connectorByKey.get(error.getConnectorKey());
        List<ReconModuleDto> affectedModules = connector != null
                ? List.of()
                : dedupeModules(impactedModulesByConnectorKey.getOrDefault(normalizeConnectorKey(error.getConnectorKey()), List.of()));
        List<String> affectedReconViews = connector != null ? connector.getAffectedReconViews() : affectedModules.stream().map(ReconModuleDto::getReconView).toList();
        List<String> affectedReconLabels = connector != null ? connector.getAffectedReconLabels() : affectedModules.stream().map(ReconModuleDto::getLabel).filter(Objects::nonNull).toList();
        String endpointMode = connector != null ? connector.getEndpointMode() : null;
        return error.toBuilder()
                .endpointMode(endpointMode)
                .sharedAsset(affectedReconViews != null && affectedReconViews.size() > 1)
                .affectedReconViews(affectedReconViews == null ? List.of() : affectedReconViews)
                .affectedReconLabels(affectedReconLabels == null ? List.of() : affectedReconLabels)
                .build();
    }

    private IntegrationReplayRequestDto annotateReplayRequest(String tenantId,
                                                              IntegrationReplayRequestDto request,
                                                              Map<String, IntegrationConnectorDto> connectorByKey,
                                                              Map<String, List<ReconModuleDto>> impactedModulesByConnectorKey) {
        IntegrationConnectorDto connector = connectorByKey.get(request.getConnectorKey());
        List<ReconModuleDto> affectedModules = connector != null
                ? List.of()
                : dedupeModules(impactedModulesByConnectorKey.getOrDefault(normalizeConnectorKey(request.getConnectorKey()), List.of()));
        List<String> affectedReconViews = connector != null ? connector.getAffectedReconViews() : affectedModules.stream().map(ReconModuleDto::getReconView).toList();
        List<String> affectedReconLabels = connector != null ? connector.getAffectedReconLabels() : affectedModules.stream().map(ReconModuleDto::getLabel).filter(Objects::nonNull).toList();
        String endpointMode = connector != null ? connector.getEndpointMode() : null;
        return request.toBuilder()
                .endpointMode(endpointMode)
                .sharedAsset(affectedReconViews != null && affectedReconViews.size() > 1)
                .affectedReconViews(affectedReconViews == null ? List.of() : affectedReconViews)
                .affectedReconLabels(affectedReconLabels == null ? List.of() : affectedReconLabels)
                .build();
    }

    private Map<String, List<ReconModuleDto>> impactedModulesByConnectorKey(Collection<String> allowedReconViews) {
        return reconModuleService.getModulesForReconViews(allowedReconViews).stream()
                .flatMap(module -> module.getIntegrationConnectorKeys().stream()
                        .map(connectorKey -> Map.entry(normalizeConnectorKey(connectorKey), module)))
                .filter(entry -> entry.getKey() != null)
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));
    }

    private List<ReconModuleDto> dedupeModules(List<ReconModuleDto> modules) {
        if (modules == null || modules.isEmpty()) {
            return List.of();
        }
        return modules.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        ReconModuleDto::getReconView,
                        module -> module,
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();
    }

    private java.util.Set<String> normalizeAllowedReconViews(Collection<String> allowedReconViews) {
        if (allowedReconViews == null) {
            return java.util.Set.of();
        }
        return allowedReconViews.stream()
                .map(this::normalizeValue)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private java.util.Set<String> scopeReconViews(java.util.Set<String> allowedReconViews, String reconView) {
        String normalizedReconView = normalizeValue(reconView);
        if (normalizedReconView == null) {
            return allowedReconViews;
        }
        return allowedReconViews.contains(normalizedReconView)
                ? java.util.Set.of(normalizedReconView)
                : java.util.Set.of();
    }

    private String inferReconViewForConnector(Collection<String> allowedReconViews, String connectorKey) {
        String normalizedConnectorKey = normalizeConnectorKey(connectorKey);
        if (normalizedConnectorKey == null) {
            return null;
        }
        List<String> matches = reconModuleService.getModulesForReconViews(allowedReconViews).stream()
                .filter(module -> module.getIntegrationConnectorKeys().stream()
                        .map(this::normalizeConnectorKey)
                        .anyMatch(normalizedConnectorKey::equals))
                .map(ReconModuleDto::getReconView)
                .distinct()
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private String resolveReplayReconView(String reconView,
                                          Collection<String> allowedReconViews,
                                          String connectorKey) {
        String normalizedReconView = normalizeValue(reconView);
        if (normalizedReconView != null) {
            return normalizedReconView;
        }
        return inferReconViewForConnector(allowedReconViews, connectorKey);
    }

    private String resolveEndpointMode(String tenantId, String systemName) {
        return systemEndpointProfileService.findSelectedProfile(tenantId, systemName)
                .map(profile -> profile.getEndpointMode())
                .orElse(null);
    }

    private String normalizeConnectorKey(String connectorKey) {
        String normalized = trimToNull(connectorKey);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private String normalizeValue(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void runInRequiresNewTransaction(Runnable action) {
        executeInRequiresNewTransaction(() -> {
            action.run();
            return null;
        });
    }

    private <T> T executeInRequiresNewTransaction(Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> action.get());
    }
}
