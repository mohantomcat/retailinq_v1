package com.recon.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.api.domain.OperationActionResponseDto;
import com.recon.api.domain.OperationModuleStatusDto;
import com.recon.api.domain.OperationsActionAudit;
import com.recon.api.domain.OperationsResponse;
import com.recon.api.domain.ReplayWindowOperationRequest;
import com.recon.api.domain.ResetCheckpointOperationRequest;
import com.recon.api.repository.OperationsActionAuditRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OperationsService {

    private final OperationsActionAuditRepository auditRepository;
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

    @Value("${app.operations.siocs-cloud-base-url}")
    private String siocsCloudBaseUrl;

    @Value("${app.operations.xocs-cloud-base-url}")
    private String xocsCloudBaseUrl;

    @Transactional(readOnly = true)
    public OperationsResponse getOperations() {
        return OperationsResponse.builder()
                .modules(List.of(
                        fetchStatus("xstore-publisher", "Xstore Publisher", "XSTORE_SIM", "source", xstoreBaseUrl, "/api/xstore-publisher/status", List.of("publish", "release-stale-claims"), List.of(), true),
                        fetchStatus("sim-poller", "SIM Poller", "XSTORE_SIM", "target", simBaseUrl, "/api/siocs-poller/status", List.of("poll", "release-lease"), List.of("reset-checkpoint"), true),
                        fetchStatus("siocs-cloud-connector", "SIOCS Cloud Connector", "XSTORE_SIOCS", "target", siocsCloudBaseUrl, "/api/cloud-connector/status", List.of("download", "publish", "release-stale-claims", "requeue-failed", "requeue-dlq"), List.of("reset-checkpoint", "replay-window"), false),
                        fetchStatus("xocs-cloud-connector", "XOCS Cloud Connector", "XSTORE_XOCS", "target", xocsCloudBaseUrl, "/api/xocs-connector/status", List.of("download", "publish", "release-stale-claims", "requeue-failed"), List.of("reset-checkpoint", "replay-window"), false)
                ))
                .build();
    }

    @Transactional
    public OperationActionResponseDto executeSafeAction(String tenantId,
                                                        String moduleId,
                                                        String actionKey,
                                                        String username) {
        ModuleDef def = module(moduleId);
        if (!def.safeActions.contains(actionKey)) {
            throw new IllegalArgumentException("Unsupported safe action: " + actionKey);
        }

        String path = switch (moduleId) {
            case "xstore-publisher" -> "/api/xstore-publisher/actions/" + actionKey;
            case "sim-poller" -> "/api/siocs-poller/actions/" + actionKey;
            case "siocs-cloud-connector" -> "/api/cloud-connector/actions/" + actionKey;
            case "xocs-cloud-connector" -> "/api/xocs-connector/actions/" + actionKey;
            default -> throw new IllegalArgumentException("Unsupported module: " + moduleId);
        };

        Map<String, Object> response = post(def.baseUrl + path, null, def.basicAuth);
        OperationActionResponseDto dto = OperationActionResponseDto.builder()
                .moduleId(moduleId)
                .actionKey(actionKey)
                .status(stringValue(response.getOrDefault("status", "OK")))
                .message(stringValue(response.getOrDefault("message", "Action completed")))
                .rawResponse(response)
                .build();

        auditRepository.save(OperationsActionAudit.builder()
                .tenantId(tenantId)
                .moduleId(moduleId)
                .actionKey(actionKey)
                .actionScope("SAFE")
                .requestedBy(username)
                .requestPayload(null)
                .resultStatus(dto.getStatus())
                .resultMessage(dto.getMessage())
                .build());

        return dto;
    }

    @Transactional
    public OperationActionResponseDto resetCheckpoint(String tenantId,
                                                      String moduleId,
                                                      String username,
                                                      ResetCheckpointOperationRequest request) {
        ModuleDef def = module(moduleId);
        if (!def.advancedActions.contains("reset-checkpoint")) {
            throw new IllegalArgumentException("Reset checkpoint is not supported for module: " + moduleId);
        }

        String path = switch (moduleId) {
            case "sim-poller" -> "/api/siocs-poller/actions/reset-checkpoint";
            case "siocs-cloud-connector" -> "/api/cloud-connector/actions/reset-checkpoint";
            case "xocs-cloud-connector" -> "/api/xocs-connector/actions/reset-checkpoint";
            default -> throw new IllegalArgumentException("Reset checkpoint is not supported for module: " + moduleId);
        };

        Map<String, Object> payload = switch (moduleId) {
            case "sim-poller" -> buildSimResetPayload(request);
            case "siocs-cloud-connector", "xocs-cloud-connector" -> buildCloudResetPayload(request);
            default -> Map.of();
        };

        Map<String, Object> response = post(def.baseUrl + path, payload, def.basicAuth);
        OperationActionResponseDto dto = toActionResponse(moduleId, "reset-checkpoint", response, "Checkpoint reset");
        saveAudit(tenantId, moduleId, "reset-checkpoint", "CHECKPOINT_RESET", username, payload, dto);
        return dto;
    }

    @Transactional
    public OperationActionResponseDto replayWindow(String tenantId,
                                                   String moduleId,
                                                   String username,
                                                   ReplayWindowOperationRequest request) {
        ModuleDef def = module(moduleId);
        if (!def.advancedActions.contains("replay-window")) {
            throw new IllegalArgumentException("Replay window is not supported for module: " + moduleId);
        }

        String path = switch (moduleId) {
            case "siocs-cloud-connector" -> "/api/cloud-connector/actions/replay-window";
            case "xocs-cloud-connector" -> "/api/xocs-connector/actions/replay-window";
            default -> throw new IllegalArgumentException("Replay window is not supported for module: " + moduleId);
        };

        Map<String, Object> payload = buildReplayPayload(request);
        Map<String, Object> response = post(def.baseUrl + path, payload, def.basicAuth);
        OperationActionResponseDto dto = toActionResponse(moduleId, "replay-window", response, "Replay window queued");
        saveAudit(tenantId, moduleId, "replay-window", "ADVANCED", username, payload, dto);
        return dto;
    }

    private OperationModuleStatusDto fetchStatus(String moduleId,
                                                 String moduleLabel,
                                                 String reconView,
                                                 String category,
                                                 String baseUrl,
                                                 String path,
                                                 List<String> actions,
                                                 List<String> advancedActions,
                                                 boolean basicAuth) {
        try {
            Map<String, Object> status = get(baseUrl + path, basicAuth);
            return OperationModuleStatusDto.builder()
                    .moduleId(moduleId)
                    .moduleLabel(moduleLabel)
                    .reconView(reconView)
                    .category(category)
                    .reachable(true)
                    .availableActions(actions)
                    .advancedActions(advancedActions)
                    .status(status)
                    .build();
        } catch (Exception ex) {
            return OperationModuleStatusDto.builder()
                    .moduleId(moduleId)
                    .moduleLabel(moduleLabel)
                    .reconView(reconView)
                    .category(category)
                    .reachable(false)
                    .availableActions(actions)
                    .advancedActions(advancedActions)
                    .status(Map.of("error", ex.getMessage()))
                    .build();
        }
    }

    private ModuleDef module(String moduleId) {
        return switch (moduleId) {
            case "xstore-publisher" -> new ModuleDef(xstoreBaseUrl, List.of("publish", "release-stale-claims"), List.of(), true);
            case "sim-poller" -> new ModuleDef(simBaseUrl, List.of("poll", "release-lease"), List.of("reset-checkpoint"), true);
            case "siocs-cloud-connector" -> new ModuleDef(siocsCloudBaseUrl, List.of("download", "publish", "release-stale-claims", "requeue-failed", "requeue-dlq"), List.of("reset-checkpoint", "replay-window"), false);
            case "xocs-cloud-connector" -> new ModuleDef(xocsCloudBaseUrl, List.of("download", "publish", "release-stale-claims", "requeue-failed"), List.of("reset-checkpoint", "replay-window"), false);
            default -> throw new IllegalArgumentException("Unsupported module: " + moduleId);
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

    private void saveAudit(String tenantId,
                           String moduleId,
                           String actionKey,
                           String scope,
                           String username,
                           Map<String, Object> payload,
                           OperationActionResponseDto dto) {
        auditRepository.save(OperationsActionAudit.builder()
                .tenantId(tenantId)
                .moduleId(moduleId)
                .actionKey(actionKey)
                .actionScope(scope)
                .requestedBy(username)
                .requestPayload(writeJson(payload))
                .resultStatus(dto.getStatus())
                .resultMessage(dto.getMessage())
                .build());
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

    private record ModuleDef(String baseUrl, List<String> safeActions, List<String> advancedActions, boolean basicAuth) {
    }
}
