package com.recon.api.service;

import com.recon.api.domain.SystemEndpointOptionDto;
import com.recon.api.domain.SystemEndpointProfileDto;
import com.recon.api.domain.SystemEndpointRuntimeCatalog;
import com.recon.api.domain.SystemEndpointSelectionRequest;
import com.recon.api.domain.TenantSystemEndpointProfile;
import com.recon.api.repository.SystemEndpointRuntimeCatalogRepository;
import com.recon.api.repository.TenantSystemEndpointProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SystemEndpointProfileService {

    private final SystemEndpointRuntimeCatalogRepository runtimeCatalogRepository;
    private final TenantSystemEndpointProfileRepository tenantProfileRepository;

    @Transactional
    public List<SystemEndpointProfileDto> getTenantProfiles(String tenantId) {
        ensureDefaultTenantProfiles(tenantId);
        Map<String, TenantSystemEndpointProfile> selectedProfilesBySystem = tenantProfileRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(
                        profile -> normalizeValue(profile.getSystemName()),
                        profile -> profile,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));

        Map<String, List<SystemEndpointRuntimeCatalog>> optionsBySystem = runtimeCatalogRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .collect(Collectors.groupingBy(
                        profile -> normalizeValue(profile.getSystemName()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return optionsBySystem.entrySet().stream()
                .map(entry -> toDto(entry.getKey(), entry.getValue(), selectedProfilesBySystem.get(entry.getKey())))
                .toList();
    }

    @Transactional
    public List<SystemEndpointProfileDto> saveTenantProfiles(String tenantId,
                                                             List<SystemEndpointSelectionRequest> selections,
                                                             String actor) {
        ensureDefaultTenantProfiles(tenantId);
        List<SystemEndpointSelectionRequest> safeSelections = selections == null ? List.of() : selections;
        for (SystemEndpointSelectionRequest selection : safeSelections) {
            String systemName = normalizeValue(selection != null ? selection.getSystemName() : null);
            if (systemName == null) {
                throw new IllegalArgumentException("System name is required");
            }
            String endpointMode = normalizeValue(selection.getEndpointMode());
            if (endpointMode == null) {
                tenantProfileRepository.deleteByTenantIdAndSystemNameIgnoreCase(tenantId, systemName);
                continue;
            }
            SystemEndpointRuntimeCatalog runtime = runtimeCatalogRepository
                    .findBySystemNameIgnoreCaseAndEndpointModeIgnoreCase(systemName, endpointMode)
                    .filter(SystemEndpointRuntimeCatalog::isActive)
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported endpoint mode " + endpointMode + " for " + systemName));
            if (!runtime.isImplemented()) {
                throw new IllegalArgumentException("Endpoint mode " + endpointMode + " for " + systemName + " is not implemented yet");
            }

            TenantSystemEndpointProfile profile = tenantProfileRepository.findByTenantIdAndSystemNameIgnoreCase(tenantId, systemName)
                    .orElseGet(() -> TenantSystemEndpointProfile.builder()
                            .tenantId(tenantId)
                            .systemName(systemName)
                            .build());
            profile.setEndpointMode(endpointMode);
            profile.setConnectorModuleId(trimToNull(runtime.getConnectorModuleId()));
            profile.setUpdatedBy(defaultIfBlank(actor, "system"));
            tenantProfileRepository.save(profile);
        }
        ensureDefaultTenantProfiles(tenantId);
        return getTenantProfiles(tenantId);
    }

    @Transactional(readOnly = true)
    public Optional<SystemEndpointOptionDto> findSelectedProfile(String tenantId, String systemName) {
        String normalizedSystemName = normalizeValue(systemName);
        if (normalizedSystemName == null) {
            return Optional.empty();
        }
        return tenantProfileRepository.findByTenantIdAndSystemNameIgnoreCase(tenantId, normalizedSystemName)
                .flatMap(selection -> runtimeCatalogRepository.findBySystemNameIgnoreCaseAndEndpointModeIgnoreCase(
                        normalizedSystemName,
                        selection.getEndpointMode()
                ))
                .filter(SystemEndpointRuntimeCatalog::isActive)
                .map(this::toOptionDto);
    }

    private void ensureDefaultTenantProfiles(String tenantId) {
        String normalizedTenantId = trimToNull(tenantId);
        if (normalizedTenantId == null) {
            return;
        }
        Map<String, TenantSystemEndpointProfile> existingSelections = tenantProfileRepository.findByTenantId(normalizedTenantId).stream()
                .collect(Collectors.toMap(
                        profile -> normalizeValue(profile.getSystemName()),
                        profile -> profile,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
        Map<String, List<SystemEndpointRuntimeCatalog>> runtimeOptionsBySystem = runtimeCatalogRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .collect(Collectors.groupingBy(
                        runtime -> normalizeValue(runtime.getSystemName()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (Map.Entry<String, List<SystemEndpointRuntimeCatalog>> entry : runtimeOptionsBySystem.entrySet()) {
            if (existingSelections.containsKey(entry.getKey())) {
                continue;
            }
            entry.getValue().stream()
                    .filter(SystemEndpointRuntimeCatalog::isImplemented)
                    .filter(SystemEndpointRuntimeCatalog::isDefaultSelection)
                    .findFirst()
                    .ifPresent(runtime -> tenantProfileRepository.save(TenantSystemEndpointProfile.builder()
                            .tenantId(normalizedTenantId)
                            .systemName(entry.getKey())
                            .endpointMode(normalizeValue(runtime.getEndpointMode()))
                            .connectorModuleId(trimToNull(runtime.getConnectorModuleId()))
                            .updatedBy("system")
                            .build()));
        }
    }

    private SystemEndpointProfileDto toDto(String systemName,
                                           List<SystemEndpointRuntimeCatalog> runtimes,
                                           TenantSystemEndpointProfile selectedProfile) {
        List<SystemEndpointOptionDto> options = runtimes.stream()
                .map(this::toOptionDto)
                .toList();
        SystemEndpointOptionDto selectedOption = options.stream()
                .filter(option -> selectedProfile != null
                        && normalizeValue(selectedProfile.getEndpointMode()).equals(option.getEndpointMode()))
                .findFirst()
                .orElse(options.stream()
                        .filter(SystemEndpointOptionDto::isDefaultSelection)
                        .filter(SystemEndpointOptionDto::isImplemented)
                        .findFirst()
                        .orElse(options.isEmpty() ? null : options.get(0)));

        return SystemEndpointProfileDto.builder()
                .systemName(systemName)
                .systemLabel(selectedOption != null ? selectedOption.getSystemLabel() : prettify(systemName))
                .selectedEndpointMode(selectedOption != null ? selectedOption.getEndpointMode() : null)
                .selectedConnectorModuleId(selectedOption != null ? selectedOption.getConnectorModuleId() : null)
                .selectedConnectorLabel(selectedOption != null ? selectedOption.getConnectorLabel() : null)
                .selectedIntegrationConnectorKey(selectedOption != null ? selectedOption.getIntegrationConnectorKey() : null)
                .selectedBaseUrlKey(selectedOption != null ? selectedOption.getBaseUrlKey() : null)
                .options(options)
                .build();
    }

    private SystemEndpointOptionDto toOptionDto(SystemEndpointRuntimeCatalog runtime) {
        return SystemEndpointOptionDto.builder()
                .systemName(normalizeValue(runtime.getSystemName()))
                .systemLabel(defaultIfBlank(trimToNull(runtime.getSystemLabel()), prettify(runtime.getSystemName())))
                .endpointMode(normalizeValue(runtime.getEndpointMode()))
                .connectorModuleId(trimToNull(runtime.getConnectorModuleId()))
                .connectorLabel(trimToNull(runtime.getConnectorLabel()))
                .integrationConnectorKey(trimToNull(runtime.getIntegrationConnectorKey()))
                .baseUrlKey(normalizeValue(runtime.getBaseUrlKey()))
                .displayOrder(runtime.getDisplayOrder())
                .implemented(runtime.isImplemented())
                .defaultSelection(runtime.isDefaultSelection())
                .notes(trimToNull(runtime.getNotes()))
                .build();
    }

    private String normalizeValue(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
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

    private String prettify(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return "";
        }
        if (trimmed.equalsIgnoreCase("XSTORE") || trimmed.equalsIgnoreCase("XOCS")
                || trimmed.equalsIgnoreCase("SIM") || trimmed.equalsIgnoreCase("SIOCS")
                || trimmed.equalsIgnoreCase("MFCS") || trimmed.equalsIgnoreCase("RMS")) {
            return trimmed.toUpperCase(Locale.ROOT);
        }
        return java.util.Arrays.stream(trimmed.split("[_\\s]+"))
                .filter(Objects::nonNull)
                .map(token -> token.isBlank()
                        ? token
                        : token.substring(0, 1).toUpperCase(Locale.ROOT) + token.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }
}
