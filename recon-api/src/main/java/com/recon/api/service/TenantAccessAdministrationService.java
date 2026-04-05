package com.recon.api.service;

import com.recon.api.domain.AuditLedgerWriteRequest;
import com.recon.api.domain.AssignUserOrganizationScopesRequest;
import com.recon.api.domain.CreateTenantApiKeyRequest;
import com.recon.api.domain.CreatedTenantApiKeyResponse;
import com.recon.api.domain.LoginOptionsResponse;
import com.recon.api.domain.OrganizationUnit;
import com.recon.api.domain.OrganizationUnitDto;
import com.recon.api.domain.ReconGroupCatalog;
import com.recon.api.domain.ReconGroupSelectionAssignmentRequest;
import com.recon.api.domain.ReconGroupSelectionDto;
import com.recon.api.domain.ReconModuleDto;
import com.recon.api.domain.SaveOrganizationUnitRequest;
import com.recon.api.domain.SaveTenantAuthConfigRequest;
import com.recon.api.domain.SaveTenantReconGroupSelectionsRequest;
import com.recon.api.domain.SaveTenantSystemEndpointProfilesRequest;
import com.recon.api.domain.SystemEndpointProfileDto;
import com.recon.api.domain.TenantAccessCenterResponse;
import com.recon.api.domain.TenantApiKey;
import com.recon.api.domain.TenantApiKeyDto;
import com.recon.api.domain.TenantAuthConfigDto;
import com.recon.api.domain.TenantAuthConfigEntity;
import com.recon.api.domain.TenantGroupSelection;
import com.recon.api.domain.User;
import com.recon.api.domain.UserOrganizationScope;
import com.recon.api.domain.UserOrganizationScopeDto;
import com.recon.api.repository.OrganizationUnitRepository;
import com.recon.api.repository.PermissionRepository;
import com.recon.api.repository.ReconGroupCatalogRepository;
import com.recon.api.repository.TenantApiKeyRepository;
import com.recon.api.repository.TenantAuthConfigRepository;
import com.recon.api.repository.TenantGroupSelectionRepository;
import com.recon.api.repository.UserOrganizationScopeRepository;
import com.recon.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantAccessAdministrationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccessScopeService accessScopeService;
    private final TenantService tenantService;
    private final UserRepository userRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final UserOrganizationScopeRepository userOrganizationScopeRepository;
    private final TenantAuthConfigRepository tenantAuthConfigRepository;
    private final TenantApiKeyRepository tenantApiKeyRepository;
    private final PermissionRepository permissionRepository;
    private final ReconGroupCatalogRepository reconGroupCatalogRepository;
    private final TenantGroupSelectionRepository tenantGroupSelectionRepository;
    private final ReconModuleService reconModuleService;
    private final SystemEndpointProfileService systemEndpointProfileService;
    private final AuditLedgerService auditLedgerService;

    @Transactional
    public TenantAccessCenterResponse getAccessCenter(String tenantId) {
        accessScopeService.ensureTenantHierarchy(tenantId, "system");
        return TenantAccessCenterResponse.builder()
                .authConfig(toDto(resolveAuthConfig(tenantId)))
                .apiKeys(tenantApiKeyRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream()
                        .map(this::toDto)
                        .toList())
                .storeCatalog(accessScopeService.getTenantStoreCatalog(tenantId))
                .reconGroups(reconModuleService.getTenantReconGroups(tenantId))
                .systemEndpointProfiles(systemEndpointProfileService.getTenantProfiles(tenantId))
                .build();
    }

    @Transactional
    public List<OrganizationUnitDto> getOrganizationUnits(String tenantId) {
        accessScopeService.ensureTenantHierarchy(tenantId, "system");
        return accessScopeService.getOrganizationUnits(tenantId);
    }

    @Transactional
    public OrganizationUnitDto saveOrganizationUnit(String tenantId,
                                                    UUID unitId,
                                                    SaveOrganizationUnitRequest request,
                                                    String actor) {
        accessScopeService.ensureTenantHierarchy(tenantId, actor);

        SaveOrganizationUnitRequest safeRequest = request != null ? request : new SaveOrganizationUnitRequest();
        String unitKey = trimToNull(safeRequest.getUnitKey());
        String unitName = trimToNull(safeRequest.getUnitName());
        String unitType = normalizeType(safeRequest.getUnitType());
        if (unitKey == null) {
            throw new IllegalArgumentException("Unit key is required");
        }
        if (unitName == null) {
            throw new IllegalArgumentException("Unit name is required");
        }
        if (unitType == null) {
            throw new IllegalArgumentException("Unit type is required");
        }

        OrganizationUnit unit = unitId == null
                ? OrganizationUnit.builder()
                .tenantId(tenantId)
                .createdBy(defaultActor(actor))
                .build()
                : organizationUnitRepository.findById(unitId)
                .filter(existing -> Objects.equals(existing.getTenantId(), tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Organization unit not found"));

        OrganizationUnit before = cloneForAudit(unit);
        OrganizationUnit parentUnit = resolveParentUnit(tenantId, safeRequest.getParentUnitId(), unit);
        String storeId = trimToNull(safeRequest.getStoreId());
        if ("STORE".equals(unitType) && storeId == null) {
            throw new IllegalArgumentException("Store id is required for STORE units");
        }

        unit.setUnitKey(unitKey.toUpperCase(Locale.ROOT));
        unit.setUnitName(unitName);
        unit.setUnitType(unitType);
        unit.setParentUnit(parentUnit);
        unit.setStoreId(storeId == null ? null : storeId.toUpperCase(Locale.ROOT));
        unit.setSortOrder(safeRequest.getSortOrder() != null ? safeRequest.getSortOrder() : 0);
        unit.setActive(safeRequest.getActive() == null || safeRequest.getActive());
        unit.setUpdatedBy(defaultActor(actor));

        if (unitId == null && organizationUnitRepository.existsByTenantIdAndUnitKeyIgnoreCase(tenantId, unit.getUnitKey())) {
            throw new IllegalArgumentException("Unit key already exists");
        }

        OrganizationUnit saved = organizationUnitRepository.save(unit);
        recordAudit(tenantId,
                unitId == null ? "ORG_UNIT_CREATED" : "ORG_UNIT_UPDATED",
                unitId == null ? "Organization unit created" : "Organization unit updated",
                saved.getId().toString(),
                actor,
                before.getId() == null ? null : before,
                saved);

        return accessScopeService.getOrganizationUnits(tenantId).stream()
                .filter(dto -> Objects.equals(dto.getId(), saved.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Saved organization unit could not be loaded"));
    }

    @Transactional
    public List<UserOrganizationScopeDto> assignUserOrganizationScopes(String tenantId,
                                                                       UUID userId,
                                                                       AssignUserOrganizationScopesRequest request,
                                                                       String actor) {
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        List<UUID> requestedUnitIds = request != null && request.getOrganizationUnitIds() != null
                ? request.getOrganizationUnitIds().stream().filter(Objects::nonNull).distinct().toList()
                : List.of();
        List<OrganizationUnit> units = requestedUnitIds.isEmpty()
                ? List.of()
                : organizationUnitRepository.findAllById(requestedUnitIds).stream()
                .filter(unit -> Objects.equals(unit.getTenantId(), tenantId))
                .toList();
        if (units.size() != requestedUnitIds.size()) {
            throw new IllegalArgumentException("One or more organization units are invalid");
        }

        List<UserOrganizationScope> beforeScopes = accessScopeService.loadUserOrganizationScopes(tenantId, userId);
        userOrganizationScopeRepository.deleteByTenantIdAndUser_Id(tenantId, userId);

        boolean includeDescendants = request == null || request.getIncludeDescendants() == null || request.getIncludeDescendants();
        List<UserOrganizationScope> savedScopes = new ArrayList<>();
        for (OrganizationUnit unit : units) {
            savedScopes.add(userOrganizationScopeRepository.save(UserOrganizationScope.builder()
                    .tenantId(tenantId)
                    .user(user)
                    .organizationUnit(unit)
                    .includeDescendants(includeDescendants)
                    .createdBy(defaultActor(actor))
                    .build()));
        }

        recordAudit(tenantId,
                "USER_ORG_SCOPES_ASSIGNED",
                "User organization scopes updated",
                user.getId().toString(),
                actor,
                beforeScopes.stream().map(scope -> scope.getOrganizationUnit() != null ? scope.getOrganizationUnit().getUnitKey() : null).toList(),
                savedScopes.stream().map(scope -> scope.getOrganizationUnit() != null ? scope.getOrganizationUnit().getUnitKey() : null).toList());

        return accessScopeService.summarizeUserScope(user).getOrganizationScopes();
    }

    @Transactional
    public TenantAuthConfigDto saveTenantAuthConfig(String tenantId,
                                                    SaveTenantAuthConfigRequest request,
                                                    String actor) {
        TenantAuthConfigEntity config = resolveAuthConfig(tenantId);
        TenantAuthConfigEntity before = cloneForAudit(config);
        SaveTenantAuthConfigRequest safeRequest = request != null ? request : new SaveTenantAuthConfigRequest();

        if (safeRequest.getLocalLoginEnabled() != null) {
            config.setLocalLoginEnabled(safeRequest.getLocalLoginEnabled());
        }
        if (trimToNull(safeRequest.getPreferredLoginMode()) != null) {
            config.setPreferredLoginMode(safeRequest.getPreferredLoginMode().trim().toUpperCase(Locale.ROOT));
        }
        if (safeRequest.getOidcEnabled() != null) {
            config.setOidcEnabled(safeRequest.getOidcEnabled());
        }
        config.setOidcDisplayName(trimToNull(safeRequest.getOidcDisplayName()));
        config.setOidcIssuerUrl(trimToNull(safeRequest.getOidcIssuerUrl()));
        config.setOidcClientId(trimToNull(safeRequest.getOidcClientId()));
        if (safeRequest.getSamlEnabled() != null) {
            config.setSamlEnabled(safeRequest.getSamlEnabled());
        }
        config.setSamlDisplayName(trimToNull(safeRequest.getSamlDisplayName()));
        config.setSamlEntityId(trimToNull(safeRequest.getSamlEntityId()));
        config.setSamlSsoUrl(trimToNull(safeRequest.getSamlSsoUrl()));
        if (safeRequest.getApiKeyAuthEnabled() != null) {
            config.setApiKeyAuthEnabled(safeRequest.getApiKeyAuthEnabled());
        }
        config.setUpdatedBy(defaultActor(actor));

        TenantAuthConfigEntity saved = tenantAuthConfigRepository.save(config);
        recordAudit(tenantId,
                "TENANT_AUTH_CONFIG_UPDATED",
                "Tenant auth configuration updated",
                tenantId,
                actor,
                before,
                saved);
        return toDto(saved);
    }

    @Transactional
    public List<ReconGroupSelectionDto> saveTenantReconGroupSelections(String tenantId,
                                                                       SaveTenantReconGroupSelectionsRequest request,
                                                                       String actor) {
        List<ReconGroupSelectionAssignmentRequest> requestedSelections =
                request != null && request.getSelections() != null ? request.getSelections() : List.of();
        if (requestedSelections.isEmpty()) {
            return reconModuleService.getTenantReconGroups(tenantId);
        }

        Map<String, String> beforeSelections = tenantGroupSelectionRepository.findByTenantId(tenantId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        selection -> normalizeUpper(selection.getGroupCode()),
                        selection -> normalizeUpper(selection.getSelectedReconView()),
                        (left, right) -> right,
                        LinkedHashMap::new
                ));

        Map<String, ReconGroupCatalog> groupsByCode = reconGroupCatalogRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .collect(java.util.stream.Collectors.toMap(
                        group -> normalizeUpper(group.getGroupCode()),
                        group -> group,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, ReconModuleDto> modulesByReconView = reconModuleService.getAllActiveModules().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ReconModuleDto::getReconView,
                        module -> module,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, String> requestedSelectionByGroupCode = new LinkedHashMap<>();

        for (ReconGroupSelectionAssignmentRequest selectionRequest : requestedSelections) {
            String groupCode = normalizeUpper(selectionRequest != null ? selectionRequest.getGroupCode() : null);
            if (groupCode == null) {
                throw new IllegalArgumentException("Group code is required");
            }
            ReconGroupCatalog group = groupsByCode.get(groupCode);
            if (group == null) {
                throw new IllegalArgumentException("Unknown reconciliation group: " + groupCode);
            }

            String selectedReconView = normalizeUpper(selectionRequest.getSelectedReconView());
            requestedSelectionByGroupCode.put(groupCode, selectedReconView);
            if (selectedReconView == null) {
                continue;
            }

            ReconModuleDto selectedModule = modulesByReconView.get(selectedReconView);
            if (selectedModule == null) {
                throw new IllegalArgumentException("Unknown reconciliation lane: " + selectedReconView);
            }
            if (!groupCode.equals(selectedModule.getGroupCode())) {
                throw new IllegalArgumentException("Reconciliation lane " + selectedReconView + " does not belong to group " + groupCode);
            }
        }

        boolean strictSelectionMode = requestedSelectionByGroupCode.values().stream().anyMatch(Objects::nonNull);
        if (strictSelectionMode) {
            List<String> missingRequiredGroups = groupsByCode.values().stream()
                    .filter(ReconGroupCatalog::isSelectionRequired)
                    .map(group -> normalizeUpper(group.getGroupCode()))
                    .filter(Objects::nonNull)
                    .filter(groupCode -> requestedSelectionByGroupCode.get(groupCode) == null)
                    .toList();
            if (!missingRequiredGroups.isEmpty()) {
                throw new IllegalArgumentException("Selections are required for groups: " + String.join(", ", missingRequiredGroups));
            }
        }

        for (ReconGroupCatalog group : groupsByCode.values()) {
            String groupCode = normalizeUpper(group.getGroupCode());
            if (groupCode == null || !requestedSelectionByGroupCode.containsKey(groupCode)) {
                continue;
            }
            String selectedReconView = requestedSelectionByGroupCode.get(groupCode);
            if (selectedReconView == null) {
                tenantGroupSelectionRepository.deleteByTenantIdAndGroupCodeIgnoreCase(tenantId, groupCode);
                continue;
            }

            TenantGroupSelection selection = tenantGroupSelectionRepository.findByTenantIdAndGroupCodeIgnoreCase(tenantId, groupCode)
                    .orElseGet(() -> TenantGroupSelection.builder()
                            .tenantId(tenantId)
                            .groupCode(groupCode)
                            .build());
            selection.setSelectedReconView(selectedReconView);
            selection.setUpdatedBy(defaultActor(actor));
            tenantGroupSelectionRepository.save(selection);
        }

        Map<String, String> afterSelections = tenantGroupSelectionRepository.findByTenantId(tenantId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        selection -> normalizeUpper(selection.getGroupCode()),
                        selection -> normalizeUpper(selection.getSelectedReconView()),
                        (left, right) -> right,
                        LinkedHashMap::new
                ));

        recordAudit(tenantId,
                "TENANT_RECON_GROUP_SELECTION_UPDATED",
                "Tenant reconciliation lane selections updated",
                tenantId,
                actor,
                beforeSelections,
                afterSelections);

        return reconModuleService.getTenantReconGroups(tenantId);
    }

    @Transactional
    public List<SystemEndpointProfileDto> saveTenantSystemEndpointProfiles(String tenantId,
                                                                           SaveTenantSystemEndpointProfilesRequest request,
                                                                           String actor) {
        List<SystemEndpointProfileDto> beforeProfiles = systemEndpointProfileService.getTenantProfiles(tenantId);
        List<SystemEndpointProfileDto> afterProfiles = systemEndpointProfileService.saveTenantProfiles(
                tenantId,
                request != null ? request.getSelections() : List.of(),
                actor
        );
        recordAudit(tenantId,
                "TENANT_SYSTEM_ENDPOINT_PROFILE_UPDATED",
                "Tenant system endpoint profiles updated",
                tenantId,
                actor,
                beforeProfiles,
                afterProfiles);
        return afterProfiles;
    }

    @Transactional
    public CreatedTenantApiKeyResponse createTenantApiKey(String tenantId,
                                                          CreateTenantApiKeyRequest request,
                                                          String actor) {
        CreateTenantApiKeyRequest safeRequest = request != null ? request : new CreateTenantApiKeyRequest();
        String keyName = trimToNull(safeRequest.getKeyName());
        if (keyName == null) {
            throw new IllegalArgumentException("Key name is required");
        }

        List<String> permissionCodes = safePermissionCodes(safeRequest.getPermissionCodes());
        boolean allStoreAccess = safeRequest.getAllStoreAccess() == null || safeRequest.getAllStoreAccess();
        List<String> allowedStoreIds = allStoreAccess
                ? List.of()
                : accessScopeService.getTenantStoreCatalog(tenantId).stream()
                .filter(storeId -> normalizeStoreIds(safeRequest.getAllowedStoreIds()).contains(storeId))
                .toList();
        if (!allStoreAccess && allowedStoreIds.isEmpty()) {
            throw new IllegalArgumentException("At least one allowed store is required when all-store access is disabled");
        }

        String prefix = "rk_" + randomToken(8);
        String secret = randomToken(32);
        String plainTextKey = prefix + "." + secret;

        TenantApiKey saved = tenantApiKeyRepository.save(TenantApiKey.builder()
                .tenantId(tenantId)
                .keyName(keyName)
                .keyPrefix(prefix)
                .keyHash(TenantApiKeyAuthService.hashKey(plainTextKey))
                .description(trimToNull(safeRequest.getDescription()))
                .permissionCodes(String.join(",", permissionCodes))
                .active(true)
                .allStoreAccess(allStoreAccess)
                .allowedStoreIds(String.join(",", allowedStoreIds))
                .createdBy(defaultActor(actor))
                .build());

        recordAudit(tenantId,
                "TENANT_API_KEY_CREATED",
                "Tenant API key created",
                saved.getId().toString(),
                actor,
                null,
                toDto(saved));

        return CreatedTenantApiKeyResponse.builder()
                .apiKey(toDto(saved))
                .plainTextKey(plainTextKey)
                .build();
    }

    @Transactional
    public TenantApiKeyDto deactivateTenantApiKey(String tenantId,
                                                  UUID apiKeyId,
                                                  String actor) {
        TenantApiKey apiKey = tenantApiKeyRepository.findByIdAndTenantId(apiKeyId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("API key not found"));
        TenantApiKey before = cloneForAudit(apiKey);
        apiKey.setActive(false);
        apiKey.setLastUsedBy(defaultActor(actor));
        TenantApiKey saved = tenantApiKeyRepository.save(apiKey);
        recordAudit(tenantId,
                "TENANT_API_KEY_DEACTIVATED",
                "Tenant API key deactivated",
                saved.getId().toString(),
                actor,
                toDto(before),
                toDto(saved));
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public LoginOptionsResponse getLoginOptions(String tenantId) {
        TenantAuthConfigEntity config = resolveAuthConfig(tenantId);
        var tenant = tenantService.getTenant(tenantId);
        return LoginOptionsResponse.builder()
                .tenantId(tenantId)
                .tenantName(tenant.getTenantName())
                .localLoginEnabled(config.isLocalLoginEnabled())
                .preferredLoginMode(config.getPreferredLoginMode())
                .oidcEnabled(config.isOidcEnabled())
                .oidcDisplayName(config.getOidcDisplayName())
                .samlEnabled(config.isSamlEnabled())
                .samlDisplayName(config.getSamlDisplayName())
                .apiKeyAuthEnabled(config.isApiKeyAuthEnabled())
                .build();
    }

    private TenantAuthConfigEntity resolveAuthConfig(String tenantId) {
        return tenantAuthConfigRepository.findById(tenantId)
                .orElseGet(() -> tenantAuthConfigRepository.save(TenantAuthConfigEntity.builder()
                        .tenantId(tenantId)
                        .localLoginEnabled(true)
                        .preferredLoginMode("LOCAL")
                        .oidcEnabled(false)
                        .samlEnabled(false)
                        .apiKeyAuthEnabled(false)
                        .updatedBy("system")
                        .build()));
    }

    private OrganizationUnit resolveParentUnit(String tenantId, UUID parentUnitId, OrganizationUnit unit) {
        if (parentUnitId == null) {
            return "ORGANIZATION".equals(normalizeType(unit.getUnitType()))
                    ? null
                    : organizationUnitRepository.findByTenantIdAndUnitKeyIgnoreCase(tenantId, "ROOT").orElse(null);
        }
        OrganizationUnit parent = organizationUnitRepository.findById(parentUnitId)
                .filter(existing -> Objects.equals(existing.getTenantId(), tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Parent organization unit not found"));
        if (unit.getId() != null && Objects.equals(unit.getId(), parent.getId())) {
            throw new IllegalArgumentException("Organization unit cannot parent itself");
        }
        return parent;
    }

    private List<String> safePermissionCodes(List<String> permissionCodes) {
        Set<String> allowedCodes = permissionRepository.findAll().stream()
                .map(permission -> permission.getCode().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return List.of("RECON_VIEW");
        }
        List<String> normalized = permissionCodes.stream()
                .map(code -> trimToNull(code))
                .filter(Objects::nonNull)
                .map(code -> code.toUpperCase(Locale.ROOT))
                .filter(allowedCodes::contains)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("No valid permission codes supplied");
        }
        return normalized;
    }

    private List<String> normalizeStoreIds(List<String> storeIds) {
        if (storeIds == null) {
            return List.of();
        }
        return storeIds.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .map(storeId -> storeId.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private TenantAuthConfigDto toDto(TenantAuthConfigEntity entity) {
        return TenantAuthConfigDto.builder()
                .tenantId(entity.getTenantId())
                .localLoginEnabled(entity.isLocalLoginEnabled())
                .preferredLoginMode(entity.getPreferredLoginMode())
                .oidcEnabled(entity.isOidcEnabled())
                .oidcDisplayName(entity.getOidcDisplayName())
                .oidcIssuerUrl(entity.getOidcIssuerUrl())
                .oidcClientId(entity.getOidcClientId())
                .samlEnabled(entity.isSamlEnabled())
                .samlDisplayName(entity.getSamlDisplayName())
                .samlEntityId(entity.getSamlEntityId())
                .samlSsoUrl(entity.getSamlSsoUrl())
                .apiKeyAuthEnabled(entity.isApiKeyAuthEnabled())
                .updatedAt(entity.getUpdatedAt())
                .updatedBy(entity.getUpdatedBy())
                .build();
    }

    private TenantApiKeyDto toDto(TenantApiKey entity) {
        return TenantApiKeyDto.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .keyName(entity.getKeyName())
                .keyPrefix(entity.getKeyPrefix())
                .description(entity.getDescription())
                .permissionCodes(splitCsv(entity.getPermissionCodes()))
                .active(entity.isActive())
                .allStoreAccess(entity.isAllStoreAccess())
                .allowedStoreIds(splitCsv(entity.getAllowedStoreIds()))
                .lastUsedAt(entity.getLastUsedAt())
                .lastUsedBy(entity.getLastUsedBy())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private void recordAudit(String tenantId,
                             String actionType,
                             String title,
                             String entityKey,
                             String actor,
                             Object before,
                             Object after) {
        auditLedgerService.record(AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("SECURITY")
                .moduleKey("SECURITY")
                .entityType("TENANT_ACCESS")
                .entityKey(entityKey)
                .actionType(actionType)
                .title(title)
                .actor(defaultActor(actor))
                .status(actionType)
                .referenceKey(entityKey)
                .controlFamily("ACCESS_CONTROL")
                .evidenceTags(List.of("SECURITY", "TENANT_ACCESS"))
                .beforeState(before)
                .afterState(after)
                .build());
    }

    private List<String> splitCsv(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return List.of();
        }
        return java.util.Arrays.stream(trimmed.split(","))
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .toList();
    }

    private String randomToken(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(length);
        for (byte value : bytes) {
            int index = Math.floorMod(value, 36);
            builder.append((char) (index < 10 ? '0' + index : 'a' + (index - 10)));
        }
        return builder.toString();
    }

    private String normalizeType(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String normalizeUpper(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultActor(String actor) {
        String trimmed = trimToNull(actor);
        return trimmed == null ? "system" : trimmed;
    }

    private OrganizationUnit cloneForAudit(OrganizationUnit unit) {
        return unit == null ? OrganizationUnit.builder().build() : OrganizationUnit.builder()
                .id(unit.getId())
                .tenantId(unit.getTenantId())
                .unitKey(unit.getUnitKey())
                .unitName(unit.getUnitName())
                .unitType(unit.getUnitType())
                .parentUnit(unit.getParentUnit())
                .storeId(unit.getStoreId())
                .sortOrder(unit.getSortOrder())
                .active(unit.isActive())
                .createdBy(unit.getCreatedBy())
                .updatedBy(unit.getUpdatedBy())
                .createdAt(unit.getCreatedAt())
                .updatedAt(unit.getUpdatedAt())
                .build();
    }

    private TenantAuthConfigEntity cloneForAudit(TenantAuthConfigEntity entity) {
        return TenantAuthConfigEntity.builder()
                .tenantId(entity.getTenantId())
                .localLoginEnabled(entity.isLocalLoginEnabled())
                .preferredLoginMode(entity.getPreferredLoginMode())
                .oidcEnabled(entity.isOidcEnabled())
                .oidcDisplayName(entity.getOidcDisplayName())
                .oidcIssuerUrl(entity.getOidcIssuerUrl())
                .oidcClientId(entity.getOidcClientId())
                .samlEnabled(entity.isSamlEnabled())
                .samlDisplayName(entity.getSamlDisplayName())
                .samlEntityId(entity.getSamlEntityId())
                .samlSsoUrl(entity.getSamlSsoUrl())
                .apiKeyAuthEnabled(entity.isApiKeyAuthEnabled())
                .updatedBy(entity.getUpdatedBy())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private TenantApiKey cloneForAudit(TenantApiKey entity) {
        return TenantApiKey.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .keyName(entity.getKeyName())
                .keyPrefix(entity.getKeyPrefix())
                .keyHash(entity.getKeyHash())
                .description(entity.getDescription())
                .permissionCodes(entity.getPermissionCodes())
                .active(entity.isActive())
                .allStoreAccess(entity.isAllStoreAccess())
                .allowedStoreIds(entity.getAllowedStoreIds())
                .lastUsedAt(entity.getLastUsedAt())
                .lastUsedBy(entity.getLastUsedBy())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
