package com.recon.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.api.domain.OperationModuleCatalogEntry;
import com.recon.api.domain.Permission;
import com.recon.api.domain.ReconGroupCatalog;
import com.recon.api.domain.ReconGroupSelectionDto;
import com.recon.api.domain.ReconModuleCatalog;
import com.recon.api.domain.ReconModuleDto;
import com.recon.api.domain.Role;
import com.recon.api.domain.TenantGroupSelection;
import com.recon.api.repository.PermissionRepository;
import com.recon.api.repository.ReconGroupCatalogRepository;
import com.recon.api.repository.ReconModuleCatalogRepository;
import com.recon.api.repository.RoleRepository;
import com.recon.api.repository.TenantGroupSelectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReconModuleService {

    private final ReconModuleCatalogRepository reconModuleCatalogRepository;
    private final ReconGroupCatalogRepository reconGroupCatalogRepository;
    private final TenantGroupSelectionRepository tenantGroupSelectionRepository;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public List<ReconModuleDto> getAllActiveModules() {
        return buildAllActiveModuleDtos();
    }

    @Transactional
    public List<ReconModuleDto> getAccessibleModules(Collection<String> permissionCodes) {
        return getAccessibleModules(null, permissionCodes);
    }

    @Transactional
    public List<ReconModuleDto> getAccessibleModules(String tenantId, Collection<String> permissionCodes) {
        Set<String> normalizedPermissions = normalizePermissionCodes(permissionCodes);
        List<ReconModuleDto> permissionAllowedModules = buildAllActiveModuleDtos().stream()
                .filter(module -> normalizedPermissions.contains(module.getPermissionCode()))
                .toList();
        return applyTenantSelections(tenantId, permissionAllowedModules);
    }

    @Transactional
    public List<ReconModuleDto> getModulesForReconViews(Collection<String> reconViews) {
        Set<String> normalizedReconViews = normalizeValues(reconViews);
        if (normalizedReconViews.isEmpty()) {
            return List.of();
        }
        return getAllActiveModules().stream()
                .filter(module -> normalizedReconViews.contains(module.getReconView()))
                .toList();
    }

    @Transactional
    public Set<String> allowedReconViews(Collection<String> permissionCodes) {
        return allowedReconViews(null, permissionCodes);
    }

    @Transactional
    public Set<String> allowedReconViews(String tenantId, Collection<String> permissionCodes) {
        return getAccessibleModules(tenantId, permissionCodes).stream()
                .map(ReconModuleDto::getReconView)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional
    public boolean hasAccess(String reconView, Collection<String> permissionCodes) {
        return hasAccess(null, reconView, permissionCodes);
    }

    @Transactional
    public boolean hasAccess(String tenantId, String reconView, Collection<String> permissionCodes) {
        String normalizedReconView = normalizeValue(reconView);
        if (normalizedReconView == null) {
            return true;
        }
        return allowedReconViews(tenantId, permissionCodes).contains(normalizedReconView);
    }

    @Transactional
    public void requireAccess(String reconView, Collection<String> permissionCodes) {
        requireAccess(null, reconView, permissionCodes);
    }

    @Transactional
    public void requireAccess(String tenantId, String reconView, Collection<String> permissionCodes) {
        String normalizedReconView = normalizeValue(reconView);
        if (normalizedReconView == null) {
            return;
        }
        if (!hasAccess(tenantId, normalizedReconView, permissionCodes)) {
            String requiredPermission = requiredPermissionForReconView(normalizedReconView).orElse(normalizedReconView);
            throw new AccessDeniedException("Missing permission or tenant access: " + requiredPermission);
        }
    }

    @Transactional
    public Optional<String> requiredPermissionForReconView(String reconView) {
        return findByReconView(reconView).map(ReconModuleDto::getPermissionCode);
    }

    @Transactional
    public boolean isValidReconView(String reconView) {
        return findByReconView(reconView).isPresent();
    }

    @Transactional
    public Optional<ReconModuleDto> findByReconView(String reconView) {
        String normalizedReconView = normalizeValue(reconView);
        if (normalizedReconView == null) {
            return Optional.empty();
        }
        return buildAllActiveModuleDtos().stream()
                .filter(module -> normalizedReconView.equals(module.getReconView()))
                .findFirst();
    }

    @Transactional
    public Optional<ReconModuleDto> findByTabId(String tabId) {
        String normalizedTabId = trimToNull(tabId);
        if (normalizedTabId == null) {
            return Optional.empty();
        }
        return buildAllActiveModuleDtos().stream()
                .filter(module -> normalizedTabId.equalsIgnoreCase(module.getTabId()))
                .findFirst();
    }

    @Transactional
    public String resolveTargetSystem(String reconView, String fallback) {
        return findByReconView(reconView)
                .map(ReconModuleDto::getTargetSystem)
                .map(this::trimToNull)
                .orElse(defaultIfBlank(fallback, "SIM"));
    }

    @Transactional
    public String resolveModuleLabel(String reconView, String fallback) {
        return findByReconView(reconView)
                .map(ReconModuleDto::getLabel)
                .map(this::trimToNull)
                .orElse(defaultIfBlank(fallback, "Unknown Module"));
    }

    @Transactional
    public boolean isTransactionFamilyScoped(String reconView) {
        return findByReconView(reconView)
                .map(ReconModuleDto::getTabId)
                .map(tabId -> !tabId.toLowerCase(Locale.ROOT).startsWith("xstore-"))
                .orElse(false);
    }

    @Transactional
    public Optional<String> findReconViewByOperationsModuleId(String moduleId) {
        String normalizedModuleId = trimToNull(moduleId);
        if (normalizedModuleId == null) {
            return Optional.empty();
        }
        List<String> matches = getAllActiveModules().stream()
                .filter(module -> module.getOperationModules().stream()
                        .anyMatch(entry -> normalizedModuleId.equalsIgnoreCase(entry.getModuleId())))
                .map(ReconModuleDto::getReconView)
                .distinct()
                .toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    @Transactional
    public Optional<OperationModuleCatalogEntry> findOperationModule(String moduleId) {
        String normalizedModuleId = trimToNull(moduleId);
        if (normalizedModuleId == null) {
            return Optional.empty();
        }
        return getAllActiveModules().stream()
                .flatMap(module -> module.getOperationModules().stream())
                .filter(entry -> normalizedModuleId.equalsIgnoreCase(entry.getModuleId()))
                .findFirst();
    }

    @Transactional
    public Optional<OperationModuleCatalogEntry> findOperationModule(String moduleId, String reconView) {
        String normalizedModuleId = trimToNull(moduleId);
        String normalizedReconView = normalizeValue(reconView);
        if (normalizedModuleId == null) {
            return Optional.empty();
        }
        return getAllActiveModules().stream()
                .filter(module -> normalizedReconView == null || normalizedReconView.equals(module.getReconView()))
                .flatMap(module -> module.getOperationModules().stream())
                .filter(entry -> normalizedModuleId.equalsIgnoreCase(entry.getModuleId()))
                .findFirst();
    }

    @Transactional
    public List<OperationModuleCatalogEntry> getAllowedOperationModules(Collection<String> allowedReconViews) {
        Set<String> normalizedAllowedViews = normalizeValues(allowedReconViews);
        return getAllActiveModules().stream()
                .filter(module -> normalizedAllowedViews.contains(module.getReconView()))
                .flatMap(module -> module.getOperationModules().stream())
                .toList();
    }

    @Transactional
    public boolean hasAccessToOperationsModule(String moduleId, Collection<String> permissionCodes) {
        return hasAccessToOperationsModule(null, moduleId, null, permissionCodes);
    }

    @Transactional
    public boolean hasAccessToOperationsModule(String tenantId,
                                               String moduleId,
                                               String reconView,
                                               Collection<String> permissionCodes) {
        String normalizedModuleId = trimToNull(moduleId);
        String normalizedReconView = normalizeValue(reconView);
        if (normalizedModuleId == null) {
            return false;
        }
        return getAccessibleModules(tenantId, permissionCodes).stream()
                .filter(module -> normalizedReconView == null || normalizedReconView.equals(module.getReconView()))
                .filter(module -> module.getOperationModules().stream()
                        .anyMatch(candidate -> normalizedModuleId.equalsIgnoreCase(candidate.getModuleId())))
                .findAny()
                .isPresent();
    }

    @Transactional
    public boolean isOperationsModuleAllowed(String moduleId, Collection<String> allowedReconViews) {
        String normalizedModuleId = trimToNull(moduleId);
        if (normalizedModuleId == null) {
            return false;
        }
        Set<String> normalizedAllowedViews = normalizeValues(allowedReconViews);
        return getAllActiveModules().stream()
                .filter(module -> module.getOperationModules().stream()
                        .anyMatch(candidate -> normalizedModuleId.equalsIgnoreCase(candidate.getModuleId())))
                .anyMatch(module -> normalizedAllowedViews.contains(module.getReconView()));
    }

    @Transactional
    public Optional<String> findReconViewByConfigurationModuleId(String configurationModuleId) {
        String normalizedConfigurationModuleId = trimToNull(configurationModuleId);
        if (normalizedConfigurationModuleId == null) {
            return Optional.empty();
        }
        return getAllActiveModules().stream()
                .filter(module -> normalizedConfigurationModuleId.equalsIgnoreCase(module.getConfigurationModuleId()))
                .map(ReconModuleDto::getReconView)
                .findFirst();
    }

    @Transactional
    public boolean isIntegrationConnectorAllowed(String connectorKey, Collection<String> allowedReconViews) {
        String normalizedConnectorKey = trimToNull(connectorKey);
        if (normalizedConnectorKey == null) {
            return false;
        }
        Set<String> normalizedAllowedViews = normalizeValues(allowedReconViews);
        return getAllActiveModules().stream()
                .filter(module -> normalizedAllowedViews.contains(module.getReconView()))
                .anyMatch(module -> module.getIntegrationConnectorKeys().stream()
                        .anyMatch(candidate -> candidate.equalsIgnoreCase(normalizedConnectorKey)));
    }

    @Transactional
    public Set<String> integrationConnectorKeysForAllowedViews(Collection<String> allowedReconViews) {
        Set<String> normalizedAllowedViews = normalizeValues(allowedReconViews);
        return getAllActiveModules().stream()
                .filter(module -> normalizedAllowedViews.contains(module.getReconView()))
                .flatMap(module -> module.getIntegrationConnectorKeys().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional
    public List<ReconGroupSelectionDto> getTenantReconGroups(String tenantId) {
        Map<String, ReconGroupCatalog> groupsByCode = loadActiveGroupCatalogEntries().stream()
                .collect(Collectors.toMap(
                        group -> normalizeValue(group.getGroupCode()),
                        group -> group,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<String, List<ReconModuleDto>> modulesByGroup = getAllActiveModules().stream()
                .filter(module -> module.getGroupCode() != null)
                .collect(Collectors.groupingBy(
                        ReconModuleDto::getGroupCode,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Map<String, String> selectionByGroupCode = selectionByGroupCode(tenantId);

        return groupsByCode.values().stream()
                .map(group -> ReconGroupSelectionDto.builder()
                        .groupCode(normalizeValue(group.getGroupCode()))
                        .groupLabel(trimToNull(group.getGroupLabel()))
                        .groupDescription(trimToNull(group.getGroupDescription()))
                        .displayOrder(group.getDisplayOrder())
                        .selectionRequired(group.isSelectionRequired())
                        .selectedReconView(selectionByGroupCode.get(normalizeValue(group.getGroupCode())))
                        .modules(modulesByGroup.getOrDefault(normalizeValue(group.getGroupCode()), List.of()))
                        .build())
                .toList();
    }

    private List<ReconModuleDto> buildAllActiveModuleDtos() {
        Map<String, ReconGroupCatalog> groupsByCode = loadActiveGroupCatalogEntries().stream()
                .collect(Collectors.toMap(
                        group -> normalizeValue(group.getGroupCode()),
                        group -> group,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return loadActiveModuleCatalogEntries().stream()
                .map(module -> toDto(module, groupsByCode.get(normalizeValue(module.getGroupCode()))))
                .toList();
    }

    private List<ReconModuleDto> applyTenantSelections(String tenantId, List<ReconModuleDto> modules) {
        String normalizedTenantId = trimToNull(tenantId);
        if (normalizedTenantId == null || modules.isEmpty()) {
            return modules;
        }
        Map<String, String> selectionByGroupCode = selectionByGroupCode(normalizedTenantId);
        if (selectionByGroupCode.isEmpty()) {
            return modules;
        }
        return modules.stream()
                .filter(module -> isModuleVisibleForTenantSelection(module, selectionByGroupCode))
                .toList();
    }

    private boolean isModuleVisibleForTenantSelection(ReconModuleDto module, Map<String, String> selectionByGroupCode) {
        String groupCode = normalizeValue(module.getGroupCode());
        if (groupCode == null) {
            return true;
        }
        String selectedReconView = selectionByGroupCode.get(groupCode);
        return selectedReconView != null && selectedReconView.equals(module.getReconView());
    }

    private Map<String, String> selectionByGroupCode(String tenantId) {
        String normalizedTenantId = trimToNull(tenantId);
        if (normalizedTenantId == null) {
            return Map.of();
        }
        return tenantGroupSelectionRepository.findByTenantId(normalizedTenantId).stream()
                .collect(Collectors.toMap(
                        selection -> normalizeValue(selection.getGroupCode()),
                        selection -> normalizeValue(selection.getSelectedReconView()),
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
    }

    private List<ReconModuleCatalog> loadActiveModuleCatalogEntries() {
        synchronizePermissionsAndAdminAssignments();
        return reconModuleCatalogRepository.findByActiveTrueOrderByDisplayOrderAsc();
    }

    private List<ReconGroupCatalog> loadActiveGroupCatalogEntries() {
        return reconGroupCatalogRepository.findByActiveTrueOrderByDisplayOrderAsc();
    }

    private void synchronizePermissionsAndAdminAssignments() {
        List<ReconModuleCatalog> modules = reconModuleCatalogRepository.findByActiveTrueOrderByDisplayOrderAsc();
        if (modules.isEmpty()) {
            return;
        }

        List<Role> adminRoles = roleRepository.findAll().stream()
                .filter(role -> role.getName() != null && "admin".equalsIgnoreCase(role.getName().trim()))
                .toList();

        for (ReconModuleCatalog module : modules) {
            String permissionCode = normalizeValue(module.getPermissionCode());
            if (permissionCode == null) {
                continue;
            }
            Permission permission = permissionRepository.findByCodeIgnoreCase(permissionCode)
                    .orElseGet(() -> permissionRepository.save(Permission.builder()
                            .id(UUID.nameUUIDFromBytes(("recon-module-permission:" + permissionCode).getBytes(StandardCharsets.UTF_8)))
                            .code(permissionCode)
                            .name(module.getModuleLabel() + " Reconciliation")
                            .module("RECONCILIATION")
                            .description("View and work the " + module.getModuleLabel() + " reconciliation lane.")
                            .build()));

            boolean roleAssignmentsChanged = false;
            for (Role adminRole : adminRoles) {
                if (adminRole.getPermissions().stream().noneMatch(existing -> existing.getCode().equalsIgnoreCase(permissionCode))) {
                    adminRole.getPermissions().add(permission);
                    roleAssignmentsChanged = true;
                }
            }
            if (roleAssignmentsChanged) {
                roleRepository.saveAll(adminRoles);
            }
        }
    }

    private ReconModuleDto toDto(ReconModuleCatalog module, ReconGroupCatalog group) {
        List<OperationModuleCatalogEntry> operationModules = parseOperationModules(module);
        return ReconModuleDto.builder()
                .reconView(normalizeValue(module.getReconView()))
                .tabId(trimToNull(module.getTabId()))
                .label(trimToNull(module.getModuleLabel()))
                .targetSystem(trimToNull(module.getTargetSystem()))
                .permissionCode(normalizeValue(module.getPermissionCode()))
                .groupCode(normalizeValue(module.getGroupCode()))
                .groupLabel(group != null ? trimToNull(group.getGroupLabel()) : null)
                .groupDisplayOrder(group != null ? group.getDisplayOrder() : null)
                .groupSelectionRequired(group != null && group.isSelectionRequired())
                .displayOrder(module.getDisplayOrder())
                .configurationModuleId(trimToNull(module.getConfigurationModuleId()))
                .operationsModuleIds(operationModules.isEmpty()
                        ? splitCsv(module.getOperationsModuleIds())
                        : operationModules.stream()
                        .map(OperationModuleCatalogEntry::getModuleId)
                        .filter(Objects::nonNull)
                        .toList())
                .operationModules(operationModules)
                .integrationConnectorKeys(splitCsv(module.getIntegrationConnectorKeys()))
                .build();
    }

    private List<OperationModuleCatalogEntry> parseOperationModules(ReconModuleCatalog module) {
        String reconView = normalizeValue(module.getReconView());
        String json = trimToNull(module.getOperationsModuleCatalogJson());
        if (json == null) {
            return splitCsv(module.getOperationsModuleIds()).stream()
                    .map(moduleId -> OperationModuleCatalogEntry.builder()
                            .reconView(reconView)
                            .moduleId(moduleId)
                            .moduleLabel(moduleId)
                            .build())
                    .toList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<OperationModuleCatalogEntry>>() {})
                    .stream()
                    .filter(Objects::nonNull)
                    .map(entry -> normalizeOperationModuleEntry(entry, reconView))
                    .filter(entry -> entry.getModuleId() != null)
                    .toList();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse operations module catalog for recon view: " + reconView, ex);
        }
    }

    private OperationModuleCatalogEntry normalizeOperationModuleEntry(OperationModuleCatalogEntry entry, String reconView) {
        return entry.toBuilder()
                .reconView(normalizeValue(defaultIfBlank(entry.getReconView(), reconView)))
                .moduleId(trimToNull(entry.getModuleId()))
                .moduleLabel(defaultIfBlank(trimToNull(entry.getModuleLabel()), trimToNull(entry.getModuleId())))
                .category(defaultIfBlank(trimToNull(entry.getCategory()), "system"))
                .baseUrlKey(normalizeValue(entry.getBaseUrlKey()))
                .statusPath(trimToNull(entry.getStatusPath()))
                .actionPathPrefix(trimToNull(entry.getActionPathPrefix()))
                .safeActions(normalizeStringList(entry.getSafeActions()))
                .advancedActions(normalizeStringList(entry.getAdvancedActions()))
                .resetPayloadMode(normalizeValue(entry.getResetPayloadMode()))
                .build();
    }

    private Set<String> normalizePermissionCodes(Collection<String> permissionCodes) {
        return normalizeValues(permissionCodes);
    }

    private Set<String> normalizeValues(Collection<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .map(this::normalizeValue)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> splitCsv(String csv) {
        String normalizedCsv = trimToNull(csv);
        if (normalizedCsv == null) {
            return List.of();
        }
        return java.util.Arrays.stream(normalizedCsv.split(","))
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> normalizeStringList(Collection<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String normalizeValue(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
