package com.recon.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.OperationModuleCatalogEntry;
import com.recon.api.domain.OperationActionResponseDto;
import com.recon.api.domain.ReconModuleDto;
import com.recon.api.domain.ReconJobActionCatalogDto;
import com.recon.api.domain.OperationModuleStatusDto;
import com.recon.api.domain.OperationsHealthSummaryDto;
import com.recon.api.domain.OperationsActionAudit;
import com.recon.api.domain.OperationsResponse;
import com.recon.api.domain.ReplayWindowOperationRequest;
import com.recon.api.domain.ResetCheckpointOperationRequest;
import com.recon.api.domain.TenantConfig;
import com.recon.api.repository.OperationsActionAuditRepository;
import com.recon.api.repository.ExceptionCaseRepository;
import com.recon.api.util.TimezoneConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OperationsService {

    private final OperationsActionAuditRepository auditRepository;
    private final ExceptionCaseRepository caseRepository;
    private final ExceptionSlaService exceptionSlaService;
    private final TenantService tenantService;
    private final AuditLedgerService auditLedgerService;
    private final ReconModuleService reconModuleService;
    private final SystemEndpointProfileService systemEndpointProfileService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.operations.connector-admin-user}")
    private String connectorAdminUser;

    @Value("${app.operations.connector-admin-password:}")
    private String connectorAdminPassword;

    @Value("${app.operations.xstore-base-url}")
    private String xstoreBaseUrl;

    @Value("${app.operations.sim-base-url}")
    private String simBaseUrl;

    @Value("${app.operations.rms-base-url:http://localhost:8186}")
    private String rmsBaseUrl;

    @Value("${app.operations.siocs-cloud-base-url}")
    private String siocsCloudBaseUrl;

    @Value("${app.operations.mfcs-rds-base-url}")
    private String mfcsRdsBaseUrl;

    @Value("${app.operations.xocs-cloud-base-url}")
    private String xocsCloudBaseUrl;

    @Transactional(readOnly = true)
    public OperationsResponse getOperations(String tenantId, java.util.Collection<String> allowedReconViews) {
        return getOperations(tenantId, allowedReconViews, null);
    }

    @Transactional(readOnly = true)
    public OperationsResponse getOperations(String tenantId,
                                           java.util.Collection<String> allowedReconViews,
                                           String reconView) {
        TenantConfig tenant = tenantService.getTenant(tenantId);
        java.util.Set<String> normalizedAllowedViews = normalizeAllowedReconViews(allowedReconViews);
        java.util.Set<String> scopedAllowedViews = scopeReconViews(normalizedAllowedViews, reconView);
        List<ExceptionCase> activeCases = caseRepository.findActiveCasesForAging(
                tenantId,
                null,
                LocalDateTime.now().minusDays(35)
        );
        Map<String, List<ExceptionCase>> casesByReconView = activeCases.stream()
                .collect(Collectors.groupingBy(
                        exceptionCase -> Objects.toString(exceptionCase.getReconView(), "").toUpperCase(),
                        Collectors.toList()));
        Map<String, List<ReconModuleDto>> impactedModulesByOperationModuleId =
                impactedModulesByOperationModuleId(normalizedAllowedViews);

        List<OperationModuleStatusDto> modules = reconModuleService.getAllowedOperationModules(scopedAllowedViews).stream()
                .map(operationModule -> fetchStatus(
                        tenantId,
                        operationModule,
                        impactedModulesByOperationModuleId.getOrDefault(normalizeModuleId(operationModule.getModuleId()), List.of())))
                .map(module -> enrichStatus(module, casesByReconView.getOrDefault(module.getReconView(), List.of()), tenant))
                .toList();

        return OperationsResponse.builder()
                .summary(buildSummary(modules))
                .modules(modules)
                .build();
    }

    public ActionSupportDescriptor describeAction(String moduleId, String actionKey) {
        return describeAction(moduleId, actionKey, null);
    }

    public ActionSupportDescriptor describeAction(String moduleId, String actionKey, String reconView) {
        String resolvedModuleId = trimToNull(moduleId);
        String resolvedActionKey = trimToNull(actionKey);
        if (resolvedModuleId == null || resolvedActionKey == null) {
            return new ActionSupportDescriptor(false, false, "NONE", "No linked operation is configured for this playbook step");
        }

        try {
            ModuleDef module = module(resolvedModuleId, reconView);
            if (module.safeActions.contains(resolvedActionKey)) {
                return new ActionSupportDescriptor(true, true, "SAFE", "Ready to run from the exception workbench");
            }
            if (module.advancedActions.contains(resolvedActionKey)) {
                return new ActionSupportDescriptor(true, false, "ADVANCED",
                        "Use the Operations console because this action requires additional parameters");
            }
            return new ActionSupportDescriptor(true, false, "UNSUPPORTED",
                    "The configured action key is not supported for this operations module");
        } catch (IllegalArgumentException ex) {
            return new ActionSupportDescriptor(true, false, "UNSUPPORTED", ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ReconJobActionCatalogDto> getReconJobActionCatalog(java.util.Collection<String> allowedReconViews) {
        java.util.Set<String> normalizedAllowedViews = allowedReconViews == null
                ? java.util.Set.of()
                : allowedReconViews.stream()
                .filter(java.util.Objects::nonNull)
                .map(value -> value.trim().toUpperCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        return reconModuleService.getAllowedOperationModules(normalizedAllowedViews).stream()
                .map(this::catalogEntry)
                .toList();
    }

    @Transactional(readOnly = true)
    public String getReconViewForModuleId(String moduleId) {
        return reconModuleService.findReconViewByOperationsModuleId(moduleId).orElse(null);
    }

    @Transactional
    public OperationActionResponseDto executeSafeAction(String tenantId,
                                                        String moduleId,
                                                        String actionKey,
                                                        String username) {
        return executeSafeAction(tenantId, moduleId, actionKey, username, null, null);
    }

    @Transactional
    public OperationActionResponseDto executeSafeAction(String tenantId,
                                                        String moduleId,
                                                        String actionKey,
                                                        String username,
                                                        String reconView) {
        return executeSafeAction(tenantId, moduleId, actionKey, username, reconView, null);
    }

    @Transactional
    public OperationActionResponseDto executePlaybookAction(String tenantId,
                                                            String moduleId,
                                                            String actionKey,
                                                            String username,
                                                            String transactionKey,
                                                            String reconView,
                                                            UUID playbookId,
                                                            UUID playbookStepId,
                                                            String playbookStepTitle) {
        return executeSafeAction(
                tenantId,
                moduleId,
                actionKey,
                username,
                reconView,
                new AuditContext(transactionKey, reconView, playbookId, playbookStepId, trimToNull(playbookStepTitle))
        );
    }

    private OperationActionResponseDto executeSafeAction(String tenantId,
                                                         String moduleId,
                                                         String actionKey,
                                                         String username,
                                                         String reconView,
                                                         AuditContext auditContext) {
        ModuleDef def = module(moduleId, reconView);
        if (!def.safeActions.contains(actionKey)) {
            throw new IllegalArgumentException("Unsupported safe action: " + actionKey);
        }

        String path = def.actionPathPrefix() + "/" + actionKey;

        try {
            Map<String, Object> response = post(def.baseUrl + path, null, def.basicAuth);
            OperationActionResponseDto dto = OperationActionResponseDto.builder()
                    .moduleId(moduleId)
                    .actionKey(actionKey)
                    .status(stringValue(response.getOrDefault("status", "OK")))
                    .message(stringValue(response.getOrDefault("message", "Action completed")))
                    .rawResponse(response)
                    .build();
            saveAudit(tenantId, moduleId, actionKey, "SAFE", username, reconView, null, dto, auditContext);
            return dto;
        } catch (RuntimeException ex) {
            OperationActionResponseDto failure = failureResponse(moduleId, actionKey, ex);
            saveAudit(tenantId, moduleId, actionKey, "SAFE", username, reconView, null, failure, auditContext);
            throw ex;
        }
    }

    @Transactional
    public OperationActionResponseDto resetCheckpoint(String tenantId,
                                                      String moduleId,
                                                      String username,
                                                      ResetCheckpointOperationRequest request) {
        return resetCheckpoint(tenantId, moduleId, username, null, request);
    }

    @Transactional
    public OperationActionResponseDto resetCheckpoint(String tenantId,
                                                      String moduleId,
                                                      String username,
                                                      String reconView,
                                                      ResetCheckpointOperationRequest request) {
        ModuleDef def = module(moduleId, reconView);
        if (!def.advancedActions.contains("reset-checkpoint")) {
            throw new IllegalArgumentException("Reset checkpoint is not supported for module: " + moduleId);
        }

        String path = def.actionPathPrefix() + "/reset-checkpoint";
        Map<String, Object> payload = buildResetPayload(def, request);

        try {
            Map<String, Object> response = post(def.baseUrl + path, payload, def.basicAuth);
            OperationActionResponseDto dto = toActionResponse(moduleId, "reset-checkpoint", response, "Checkpoint reset");
            saveAudit(tenantId, moduleId, "reset-checkpoint", "CHECKPOINT_RESET", username, reconView, payload, dto, null);
            return dto;
        } catch (RuntimeException ex) {
            OperationActionResponseDto failure = failureResponse(moduleId, "reset-checkpoint", ex);
            saveAudit(tenantId, moduleId, "reset-checkpoint", "CHECKPOINT_RESET", username, reconView, payload, failure, null);
            throw ex;
        }
    }

    @Transactional
    public OperationActionResponseDto replayWindow(String tenantId,
                                                   String moduleId,
                                                   String username,
                                                   ReplayWindowOperationRequest request) {
        return replayWindow(tenantId, moduleId, username, null, request);
    }

    @Transactional
    public OperationActionResponseDto replayWindow(String tenantId,
                                                   String moduleId,
                                                   String username,
                                                   String reconView,
                                                   ReplayWindowOperationRequest request) {
        ModuleDef def = module(moduleId, reconView);
        if (!def.advancedActions.contains("replay-window")) {
            throw new IllegalArgumentException("Replay window is not supported for module: " + moduleId);
        }

        String path = def.actionPathPrefix() + "/replay-window";

        Map<String, Object> payload = buildReplayPayload(request);
        try {
            Map<String, Object> response = post(def.baseUrl + path, payload, def.basicAuth);
            OperationActionResponseDto dto = toActionResponse(moduleId, "replay-window", response, "Replay window queued");
            saveAudit(tenantId, moduleId, "replay-window", "ADVANCED", username, reconView, payload, dto, null);
            return dto;
        } catch (RuntimeException ex) {
            OperationActionResponseDto failure = failureResponse(moduleId, "replay-window", ex);
            saveAudit(tenantId, moduleId, "replay-window", "ADVANCED", username, reconView, payload, failure, null);
            throw ex;
        }
    }

    private OperationModuleStatusDto fetchStatus(String tenantId,
                                                OperationModuleCatalogEntry operationModule,
                                                List<ReconModuleDto> affectedModules) {
        ModuleDef module = module(operationModule.getModuleId(), operationModule.getReconView());
        List<ReconModuleDto> dedupedAffectedModules = dedupeModules(affectedModules);
        List<String> affectedReconViews = dedupedAffectedModules.stream()
                .map(ReconModuleDto::getReconView)
                .toList();
        List<String> affectedReconLabels = dedupedAffectedModules.stream()
                .map(ReconModuleDto::getLabel)
                .filter(Objects::nonNull)
                .toList();
        String reconLabel = reconModuleService.resolveModuleLabel(module.reconView(), module.reconView());
        String endpointMode = resolveEndpointMode(tenantId, module.systemName(), module.baseUrlKey());
        try {
            Map<String, Object> status = get(module.baseUrl() + module.statusPath(), module.basicAuth());
            return OperationModuleStatusDto.builder()
                    .moduleId(module.moduleId())
                    .moduleLabel(module.moduleLabel())
                    .reconView(module.reconView())
                    .reconLabel(reconLabel)
                    .category(module.category())
                    .systemName(module.systemName())
                    .endpointMode(endpointMode)
                    .sharedAsset(affectedReconViews.size() > 1)
                    .affectedReconViews(affectedReconViews)
                    .affectedReconLabels(affectedReconLabels)
                    .reachable(true)
                    .availableActions(module.safeActions())
                    .advancedActions(module.advancedActions())
                    .status(status)
                    .resetPayloadMode(module.resetPayloadMode())
                    .supportsRegisterFilter(module.supportsRegisterFilter())
                    .build();
        } catch (Exception ex) {
            return OperationModuleStatusDto.builder()
                    .moduleId(module.moduleId())
                    .moduleLabel(module.moduleLabel())
                    .reconView(module.reconView())
                    .reconLabel(reconLabel)
                    .category(module.category())
                    .systemName(module.systemName())
                    .endpointMode(endpointMode)
                    .sharedAsset(affectedReconViews.size() > 1)
                    .affectedReconViews(affectedReconViews)
                    .affectedReconLabels(affectedReconLabels)
                    .reachable(false)
                    .availableActions(module.safeActions())
                    .advancedActions(module.advancedActions())
                    .status(Map.of("error", ex.getMessage()))
                    .resetPayloadMode(module.resetPayloadMode())
                    .supportsRegisterFilter(module.supportsRegisterFilter())
                    .build();
        }
    }

    private OperationModuleStatusDto enrichStatus(OperationModuleStatusDto module,
                                                  List<ExceptionCase> activeCases,
                                                  TenantConfig tenant) {
        Map<String, Object> status = module.getStatus() == null ? Map.of() : module.getStatus();
        Instant lastSuccess = resolveLastSuccess(status);
        long freshnessLagMinutes = resolveFreshnessLagMinutes(status, lastSuccess);
        long backlogCount = resolveBacklogCount(status);
        long activeCaseCount = activeCases.size();
        long breachedCaseCount = activeCases.stream()
                .filter(exceptionCase -> "BREACHED".equalsIgnoreCase(exceptionSlaService.evaluateSlaStatus(exceptionCase)))
                .count();
        ModuleDef moduleDef = module(module.getModuleId(), module.getReconView());
        String freshnessStatus = resolveFreshnessStatus(moduleDef, module.isReachable(), freshnessLagMinutes);
        String healthStatus = resolveHealthStatus(module.isReachable(), freshnessStatus, backlogCount, breachedCaseCount);
        int healthScore = resolveHealthScore(module.isReachable(), freshnessLagMinutes, backlogCount, breachedCaseCount);
        List<String> highlights = buildStatusHighlights(status, freshnessLagMinutes, backlogCount, activeCaseCount, breachedCaseCount);

        return OperationModuleStatusDto.builder()
                .moduleId(module.getModuleId())
                .moduleLabel(module.getModuleLabel())
                .reconView(module.getReconView())
                .reconLabel(module.getReconLabel())
                .category(module.getCategory())
                .systemName(module.getSystemName())
                .endpointMode(module.getEndpointMode())
                .sharedAsset(module.isSharedAsset())
                .affectedReconViews(module.getAffectedReconViews())
                .affectedReconLabels(module.getAffectedReconLabels())
                .reachable(module.isReachable())
                .availableActions(module.getAvailableActions())
                .advancedActions(module.getAdvancedActions())
                .status(module.getStatus())
                .healthStatus(healthStatus)
                .healthScore(healthScore)
                .freshnessStatus(freshnessStatus)
                .freshnessLagMinutes(freshnessLagMinutes > 0 ? freshnessLagMinutes : null)
                .backlogCount(backlogCount > 0 ? backlogCount : 0L)
                .activeCaseCount(activeCaseCount)
                .breachedCaseCount(breachedCaseCount)
                .lastSuccessfulSyncAt(lastSuccess != null
                        ? TimezoneConverter.toDisplay(lastSuccess.toString(), tenant)
                        : null)
                .statusHighlights(highlights)
                .recommendedAction(recommendedAction(healthStatus, freshnessStatus, backlogCount, breachedCaseCount))
                .resetPayloadMode(module.getResetPayloadMode())
                .supportsRegisterFilter(module.isSupportsRegisterFilter())
                .build();
    }

    private OperationsHealthSummaryDto buildSummary(List<OperationModuleStatusDto> modules) {
        return OperationsHealthSummaryDto.builder()
                .totalModules(modules.size())
                .healthyModules(modules.stream().filter(module -> "HEALTHY".equalsIgnoreCase(module.getHealthStatus())).count())
                .warningModules(modules.stream().filter(module -> "WARNING".equalsIgnoreCase(module.getHealthStatus())).count())
                .criticalModules(modules.stream().filter(module -> "CRITICAL".equalsIgnoreCase(module.getHealthStatus())).count())
                .staleModules(modules.stream().filter(module -> "STALE".equalsIgnoreCase(module.getFreshnessStatus())).count())
                .totalBacklogCount(modules.stream()
                        .map(OperationModuleStatusDto::getBacklogCount)
                        .filter(Objects::nonNull)
                        .mapToLong(Long::longValue)
                        .sum())
                .activeCasesOnUnhealthyModules(modules.stream()
                        .filter(module -> !"HEALTHY".equalsIgnoreCase(module.getHealthStatus()))
                        .map(OperationModuleStatusDto::getActiveCaseCount)
                        .filter(Objects::nonNull)
                        .mapToLong(Long::longValue)
                        .sum())
                .breachedCasesOnUnhealthyModules(modules.stream()
                        .filter(module -> !"HEALTHY".equalsIgnoreCase(module.getHealthStatus()))
                        .map(OperationModuleStatusDto::getBreachedCaseCount)
                        .filter(Objects::nonNull)
                        .mapToLong(Long::longValue)
                        .sum())
                .build();
    }

    private ModuleDef module(String moduleId) {
        return module(moduleId, null);
    }

    private ModuleDef module(String moduleId, String reconView) {
        OperationModuleCatalogEntry entry = reconModuleService.findOperationModule(moduleId, reconView)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported module: " + moduleId));
        String normalizedBaseUrlKey = normalizeBaseUrlKey(entry.getBaseUrlKey());
        return new ModuleDef(
                entry.getModuleId(),
                entry.getModuleLabel(),
                entry.getReconView(),
                entry.getCategory(),
                normalizedBaseUrlKey,
                resolveSystemName(normalizedBaseUrlKey),
                resolveBaseUrl(normalizedBaseUrlKey),
                entry.getStatusPath(),
                entry.getActionPathPrefix(),
                entry.getSafeActions(),
                entry.getAdvancedActions(),
                entry.isBasicAuth(),
                defaultIfBlank(entry.getResetPayloadMode(), "NONE"),
                entry.getFreshnessThresholdMinutes() == null ? 60 : entry.getFreshnessThresholdMinutes(),
                entry.isSupportsRegisterFilter()
        );
    }

    private String resolveBaseUrl(String baseUrlKey) {
        String normalized = normalizeBaseUrlKey(baseUrlKey);
        if (normalized == null) {
            throw new IllegalArgumentException("Base URL key is missing for operations module");
        }
        return switch (normalized) {
            case "XSTORE" -> xstoreBaseUrl;
            case "SIM" -> simBaseUrl;
            case "RMS" -> rmsBaseUrl;
            case "SIOCS_CLOUD" -> siocsCloudBaseUrl;
            case "MFCS_RDS" -> mfcsRdsBaseUrl;
            case "XOCS_CLOUD" -> xocsCloudBaseUrl;
            default -> throw new IllegalArgumentException("Unsupported operations base URL key: " + baseUrlKey);
        };
    }

    private Map<String, Object> buildSimResetPayload(ResetCheckpointOperationRequest request) {
        String timestamp = resolveTimestamp(request);
        return Map.of(
                "lastProcessedTimestamp", timestamp,
                "lastProcessedExternalId", request.getExternalId() == null ? "" : request.getExternalId(),
                "lastProcessedId", request.getProcessedId() == null ? 0L : request.getProcessedId()
        );
    }

    private Map<String, Object> buildCloudResetPayload(ResetCheckpointOperationRequest request) {
        return Map.of(
                "lastSuccessTimestamp", resolveTimestamp(request),
                "lastCursorId", request.getCursorId() == null ? 0L : request.getCursorId()
        );
    }

    private Map<String, Object> buildResetPayload(ModuleDef module, ResetCheckpointOperationRequest request) {
        return switch (defaultIfBlank(module.resetPayloadMode(), "NONE")) {
            case "DB_POLLING" -> buildSimResetPayload(request);
            case "CLOUD_CURSOR" -> buildCloudResetPayload(request);
            default -> Map.of();
        };
    }

    private Map<String, Object> buildReplayPayload(ReplayWindowOperationRequest request) {
        return Map.of(
                "replayMode", request.getReplayMode() == null || request.getReplayMode().isBlank() ? "STAGED_REPUBLISH" : request.getReplayMode(),
                "fromBusinessDate", request.getFromBusinessDate(),
                "toBusinessDate", request.getToBusinessDate(),
                "storeId", request.getStoreId() == null ? "" : request.getStoreId(),
                "wkstnId", request.getWkstnId() == null ? "" : request.getWkstnId()
        );
    }

    private String resolveTimestamp(ResetCheckpointOperationRequest request) {
        if (request.getTimestamp() != null && !request.getTimestamp().isBlank()) {
            return request.getTimestamp();
        }
        if (request.getBusinessDate() != null && !request.getBusinessDate().isBlank()) {
            LocalDate date = LocalDate.parse(request.getBusinessDate());
            return date.atStartOfDay().toInstant(ZoneOffset.UTC).toString();
        }
        return Instant.EPOCH.toString();
    }

    private OperationActionResponseDto toActionResponse(String moduleId,
                                                        String actionKey,
                                                        Map<String, Object> response,
                                                        String fallbackMessage) {
        return OperationActionResponseDto.builder()
                .moduleId(moduleId)
                .actionKey(actionKey)
                .status(stringValue(response.getOrDefault("status", "OK")))
                .message(stringValue(response.getOrDefault("message", fallbackMessage)))
                .rawResponse(response)
                .build();
    }

    private OperationActionResponseDto failureResponse(String moduleId,
                                                       String actionKey,
                                                       RuntimeException ex) {
        return OperationActionResponseDto.builder()
                .moduleId(moduleId)
                .actionKey(actionKey)
                .status("FAILED")
                .message(trimToNull(ex.getMessage()) != null ? ex.getMessage() : "Action failed")
                .rawResponse(Map.of("error", trimToNull(ex.getMessage()) != null ? ex.getMessage() : "Action failed"))
                .build();
    }

    private void saveAudit(String tenantId,
                           String moduleId,
                           String actionKey,
                           String scope,
                           String username,
                           String reconView,
                           Map<String, Object> payload,
                           OperationActionResponseDto dto,
                           AuditContext auditContext) {
        String resolvedReconView = defaultIfBlank(
                auditContext != null ? auditContext.reconView() : null,
                trimToNull(reconView)
        );
        auditRepository.save(OperationsActionAudit.builder()
                .tenantId(tenantId)
                .moduleId(moduleId)
                .actionKey(actionKey)
                .actionScope(scope)
                .requestedBy(username)
                .transactionKey(auditContext != null ? auditContext.transactionKey() : null)
                .reconView(resolvedReconView)
                .playbookId(auditContext != null ? auditContext.playbookId() : null)
                .playbookStepId(auditContext != null ? auditContext.playbookStepId() : null)
                .playbookStepTitle(auditContext != null ? auditContext.playbookStepTitle() : null)
                .requestPayload(writeJson(payload))
                .resultStatus(dto.getStatus())
                .resultMessage(dto.getMessage())
                .build());
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("moduleId", moduleId);
        metadata.put("scope", scope);
        if (auditContext != null && auditContext.playbookStepId() != null) {
            metadata.put("playbookStepId", auditContext.playbookStepId());
        }
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("OPERATIONS")
                .moduleKey(defaultIfBlank(resolvedReconView, mapOperationsModule(moduleId)))
                .entityType("OPERATIONS_ACTION")
                .entityKey(defaultIfBlank(auditContext != null ? auditContext.transactionKey() : null, moduleId))
                .actionType(defaultIfBlank(actionKey, "ACTION"))
                .title(trimToNull(auditContext != null ? auditContext.playbookStepTitle() : null) != null
                        ? "Playbook action executed"
                        : "Operations action executed")
                .summary(dto.getMessage())
                .actor(username)
                .status(dto.getStatus())
                .referenceKey(defaultIfBlank(auditContext != null ? auditContext.transactionKey() : null, moduleId))
                .controlFamily("OPERATIONS")
                .evidenceTags(List.of("OPERATIONS", scope))
                .beforeState(payload)
                .afterState(dto.getRawResponse())
                .metadata(metadata)
                .build());
    }

    private ReconJobActionCatalogDto catalogEntry(OperationModuleCatalogEntry operationModule) {
        ModuleDef module = module(operationModule.getModuleId(), operationModule.getReconView());
        return ReconJobActionCatalogDto.builder()
                .moduleId(module.moduleId())
                .moduleLabel(module.moduleLabel())
                .reconView(module.reconView())
                .availableActions(module.safeActions())
                .build();
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return payload == null || payload.isEmpty() ? null : objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"payload-serialization-failed\"}";
        }
    }

    private Map<String, Object> get(String url, boolean basicAuth) {
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers(basicAuth)), String.class);
        return toMap(response.getBody());
    }

    private Map<String, Object> post(String url, Object body, boolean basicAuth) {
        HttpHeaders headers = headers(basicAuth);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        return toMap(response.getBody());
    }

    private HttpHeaders headers(boolean basicAuth) {
        HttpHeaders headers = new HttpHeaders();
        if (basicAuth) {
            String token = Base64.getEncoder().encodeToString((connectorAdminUser + ":" + connectorAdminPassword).getBytes(StandardCharsets.UTF_8));
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + token);
        }
        return headers;
    }

    private Map<String, Object> toMap(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(body, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return Map.of("raw", body);
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Instant resolveLastSuccess(Map<String, Object> status) {
        List<Predicate<String>> keyMatchers = List.of(
                key -> key.equalsIgnoreCase("lastSuccessAt"),
                key -> key.equalsIgnoreCase("lastSuccessfulSyncAt"),
                key -> key.equalsIgnoreCase("lastProcessedAt"),
                key -> key.equalsIgnoreCase("lastPublishedAt"),
                key -> key.equalsIgnoreCase("lastDownloadAt"),
                key -> key.equalsIgnoreCase("lastPollAt"),
                key -> key.equalsIgnoreCase("updatedAt"),
                key -> key.contains("success") && key.contains("time"),
                key -> key.contains("heartbeat"),
                key -> key.contains("last") && key.contains("at")
        );
        for (Predicate<String> matcher : keyMatchers) {
            for (Map.Entry<String, Object> entry : status.entrySet()) {
                String key = entry.getKey() != null ? entry.getKey().toLowerCase() : "";
                if (matcher.test(key)) {
                    Instant parsed = parseInstant(entry.getValue());
                    if (parsed != null) {
                        return parsed;
                    }
                }
            }
        }
        return null;
    }

    private Instant parseInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            long epoch = number.longValue();
            if (String.valueOf(Math.abs(epoch)).length() >= 13) {
                return Instant.ofEpochMilli(epoch);
            }
            return Instant.ofEpochSecond(epoch);
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception ignored) {
            try {
                return OffsetDateTime.parse(text).toInstant();
            } catch (Exception ignoredAgain) {
                try {
                    return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
                } catch (Exception ignoredThird) {
                    return null;
                }
            }
        }
    }

    private long resolveFreshnessLagMinutes(Map<String, Object> status, Instant lastSuccess) {
        for (String key : status.keySet()) {
            String normalized = key == null ? "" : key.toLowerCase();
            if (normalized.contains("lag") && normalized.contains("minute")) {
                Object value = status.get(key);
                if (value instanceof Number number) {
                    return Math.max(0L, number.longValue());
                }
            }
        }
        return lastSuccess == null
                ? Long.MAX_VALUE
                : Math.max(0L, Duration.between(lastSuccess, Instant.now()).toMinutes());
    }

    private long resolveBacklogCount(Map<String, Object> status) {
        long total = 0L;
        for (Map.Entry<String, Object> entry : status.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase();
            if (!(key.contains("backlog")
                    || key.contains("queue")
                    || key.contains("pending")
                    || key.contains("failed")
                    || key.contains("dlq")
                    || key.contains("retry")
                    || key.contains("stuck"))) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Number number) {
                total += Math.max(0L, number.longValue());
            }
        }
        return total;
    }

    private String resolveFreshnessStatus(ModuleDef module, boolean reachable, long freshnessLagMinutes) {
        if (!reachable) {
            return "STALE";
        }
        long staleThreshold = module.freshnessThresholdMinutes() <= 0 ? 60L : module.freshnessThresholdMinutes();
        long warningThreshold = Math.max(15L, staleThreshold / 2L);
        if (freshnessLagMinutes == Long.MAX_VALUE || freshnessLagMinutes > staleThreshold) {
            return "STALE";
        }
        if (freshnessLagMinutes > warningThreshold) {
            return "AGING";
        }
        return "FRESH";
    }

    private String resolveHealthStatus(boolean reachable,
                                       String freshnessStatus,
                                       long backlogCount,
                                       long breachedCaseCount) {
        if (!reachable || "STALE".equalsIgnoreCase(freshnessStatus) || backlogCount >= 50 || breachedCaseCount >= 5) {
            return "CRITICAL";
        }
        if ("AGING".equalsIgnoreCase(freshnessStatus) || backlogCount >= 10 || breachedCaseCount > 0) {
            return "WARNING";
        }
        return "HEALTHY";
    }

    private int resolveHealthScore(boolean reachable,
                                   long freshnessLagMinutes,
                                   long backlogCount,
                                   long breachedCaseCount) {
        int score = 100;
        if (!reachable) {
            score -= 60;
        }
        if (freshnessLagMinutes == Long.MAX_VALUE) {
            score -= 25;
        } else {
            score -= Math.min(25, (int) (freshnessLagMinutes / 6));
        }
        score -= Math.min(20, (int) backlogCount);
        score -= Math.min(20, (int) breachedCaseCount * 4);
        return Math.max(0, score);
    }

    private List<String> buildStatusHighlights(Map<String, Object> status,
                                               long freshnessLagMinutes,
                                               long backlogCount,
                                               long activeCaseCount,
                                               long breachedCaseCount) {
        List<String> highlights = new ArrayList<>();
        if (freshnessLagMinutes != Long.MAX_VALUE) {
            highlights.add("freshness lag " + freshnessLagMinutes + " min");
        } else {
            highlights.add("no recent heartbeat");
        }
        if (backlogCount > 0) {
            highlights.add(backlogCount + " queued or failed records");
        }
        if (activeCaseCount > 0) {
            highlights.add(activeCaseCount + " open retailer cases");
        }
        if (breachedCaseCount > 0) {
            highlights.add(breachedCaseCount + " SLA breaches");
        }
        status.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .filter(entry -> entry.getKey().toLowerCase().contains("error"))
                .limit(1)
                .forEach(entry -> highlights.add(entry.getKey() + ": " + stringValue(entry.getValue())));
        return highlights.stream().distinct().limit(4).toList();
    }

    private String recommendedAction(String healthStatus,
                                     String freshnessStatus,
                                     long backlogCount,
                                     long breachedCaseCount) {
        if ("CRITICAL".equalsIgnoreCase(healthStatus)) {
            if ("STALE".equalsIgnoreCase(freshnessStatus)) {
                return "Restore connector heartbeat and confirm data is moving again";
            }
            if (backlogCount >= 50) {
                return "Reduce connector backlog and review failed records";
            }
            return "Escalate immediately and coordinate with store operations";
        }
        if ("WARNING".equalsIgnoreCase(healthStatus)) {
            if (breachedCaseCount > 0) {
                return "Triage the affected cases before they spread across more stores";
            }
            return "Monitor this integration closely and clear pending backlog";
        }
        return "Healthy. No immediate action required";
    }

    private Map<String, List<ReconModuleDto>> impactedModulesByOperationModuleId(java.util.Collection<String> allowedReconViews) {
        return reconModuleService.getModulesForReconViews(allowedReconViews).stream()
                .flatMap(module -> module.getOperationModules().stream()
                        .map(entry -> Map.entry(normalizeModuleId(entry.getModuleId()), module)))
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

    private java.util.Set<String> normalizeAllowedReconViews(java.util.Collection<String> allowedReconViews) {
        return allowedReconViews == null
                ? java.util.Set.of()
                : allowedReconViews.stream()
                .filter(java.util.Objects::nonNull)
                .map(value -> value.trim().toUpperCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private java.util.Set<String> scopeReconViews(java.util.Set<String> allowedReconViews, String reconView) {
        String normalizedReconView = trimToNull(reconView);
        if (normalizedReconView == null) {
            return allowedReconViews;
        }
        String resolved = normalizedReconView.toUpperCase(java.util.Locale.ROOT);
        return allowedReconViews.contains(resolved)
                ? java.util.Set.of(resolved)
                : java.util.Set.of();
    }

    private String normalizeBaseUrlKey(String baseUrlKey) {
        String normalized = trimToNull(baseUrlKey);
        return normalized == null ? null : normalized.toUpperCase(java.util.Locale.ROOT);
    }

    private String normalizeModuleId(String moduleId) {
        String normalized = trimToNull(moduleId);
        return normalized == null ? null : normalized.toLowerCase(java.util.Locale.ROOT);
    }

    private String resolveSystemName(String baseUrlKey) {
        String normalized = normalizeBaseUrlKey(baseUrlKey);
        if (normalized == null) {
            return "UNKNOWN";
        }
        return switch (normalized) {
            case "XSTORE" -> "XSTORE";
            case "SIM" -> "SIM";
            case "RMS" -> "RMS";
            case "SIOCS_CLOUD" -> "SIOCS";
            case "MFCS_RDS" -> "MFCS";
            case "XOCS_CLOUD" -> "XOCS";
            default -> "UNKNOWN";
        };
    }

    private String resolveEndpointMode(String tenantId, String systemName, String baseUrlKey) {
        String resolvedSystemName = trimToNull(systemName);
        if (resolvedSystemName != null) {
            return systemEndpointProfileService.findSelectedProfile(tenantId, resolvedSystemName)
                    .map(profile -> defaultIfBlank(profile.getEndpointMode(), fallbackEndpointMode(baseUrlKey)))
                    .orElse(fallbackEndpointMode(baseUrlKey));
        }
        return fallbackEndpointMode(baseUrlKey);
    }

    private String fallbackEndpointMode(String baseUrlKey) {
        return switch (normalizeBaseUrlKey(baseUrlKey)) {
            case "XSTORE", "SIM", "RMS" -> "DB";
            case "SIOCS_CLOUD", "XOCS_CLOUD" -> "REST";
            case "MFCS_RDS" -> "RDS";
            default -> "UNKNOWN";
        };
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String mapOperationsModule(String moduleId) {
        return reconModuleService.findReconViewByOperationsModuleId(moduleId)
                .orElse("OPERATIONS");
    }

    public record ActionSupportDescriptor(boolean actionConfigured,
                                          boolean actionExecutable,
                                          String actionExecutionMode,
                                          String actionSupportMessage) {
    }

    private record AuditContext(String transactionKey,
                                String reconView,
                                UUID playbookId,
                                UUID playbookStepId,
                                String playbookStepTitle) {
    }

    private record ModuleDef(String moduleId,
                             String moduleLabel,
                             String reconView,
                             String category,
                             String baseUrlKey,
                             String systemName,
                             String baseUrl,
                             String statusPath,
                             String actionPathPrefix,
                             List<String> safeActions,
                             List<String> advancedActions,
                             boolean basicAuth,
                             String resetPayloadMode,
                             int freshnessThresholdMinutes,
                             boolean supportsRegisterFilter) {
    }
}
