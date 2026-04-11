package com.recon.api.service;

import com.recon.api.domain.AuditLedgerWriteRequest;
import com.recon.api.domain.AccessGovernanceNotificationActionResultDto;
import com.recon.api.domain.AccessGovernanceApiKeyFindingDto;
import com.recon.api.domain.AccessGovernanceUserFindingDto;
import com.recon.api.domain.AssignUserOrganizationScopesRequest;
import com.recon.api.domain.CreateTenantApiKeyRequest;
import com.recon.api.domain.CreatedTenantApiKeyResponse;
import com.recon.api.domain.EmergencyAccessGrantDto;
import com.recon.api.domain.GrantEmergencyAccessRequest;
import com.recon.api.domain.LoginOptionsResponse;
import com.recon.api.domain.OidcGroupRoleMappingAssignmentRequest;
import com.recon.api.domain.OidcGroupRoleMappingDto;
import com.recon.api.domain.OrganizationUnit;
import com.recon.api.domain.OrganizationUnitDto;
import com.recon.api.domain.PrivilegedActionAlertDto;
import com.recon.api.domain.QuarterlyAccessReviewCycleResponse;
import com.recon.api.domain.ReconGroupCatalog;
import com.recon.api.domain.ReconGroupSelectionAssignmentRequest;
import com.recon.api.domain.ReconGroupSelectionDto;
import com.recon.api.domain.ReconModuleDto;
import com.recon.api.domain.RevokeEmergencyAccessRequest;
import com.recon.api.domain.Role;
import com.recon.api.domain.RoleDto;
import com.recon.api.domain.SaveOidcGroupRoleMappingsRequest;
import com.recon.api.domain.SaveOrganizationUnitRequest;
import com.recon.api.domain.SaveTenantAuthConfigRequest;
import com.recon.api.domain.SaveTenantReconGroupSelectionsRequest;
import com.recon.api.domain.SaveTenantSystemEndpointProfilesRequest;
import com.recon.api.domain.SystemEndpointProfileDto;
import com.recon.api.domain.TenantAccessCenterResponse;
import com.recon.api.domain.TenantAccessGovernanceResponse;
import com.recon.api.domain.TenantApiKey;
import com.recon.api.domain.TenantApiKeyDto;
import com.recon.api.domain.TenantAuthConfigDto;
import com.recon.api.domain.TenantAuthConfigEntity;
import com.recon.api.domain.TenantGroupSelection;
import com.recon.api.domain.TenantOidcGroupRoleMapping;
import com.recon.api.domain.User;
import com.recon.api.domain.UserOrganizationScope;
import com.recon.api.domain.UserOrganizationScopeDto;
import com.recon.api.repository.OrganizationUnitRepository;
import com.recon.api.repository.PermissionRepository;
import com.recon.api.repository.ReconGroupCatalogRepository;
import com.recon.api.repository.RoleRepository;
import com.recon.api.repository.TenantApiKeyRepository;
import com.recon.api.repository.TenantAuthConfigRepository;
import com.recon.api.repository.TenantGroupSelectionRepository;
import com.recon.api.repository.TenantOidcGroupRoleMappingRepository;
import com.recon.api.repository.UserOrganizationScopeRepository;
import com.recon.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.SecureRandom;
import java.time.LocalDateTime;
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
    private final RoleRepository roleRepository;
    private final ReconGroupCatalogRepository reconGroupCatalogRepository;
    private final TenantGroupSelectionRepository tenantGroupSelectionRepository;
    private final TenantOidcGroupRoleMappingRepository tenantOidcGroupRoleMappingRepository;
    private final ReconModuleService reconModuleService;
    private final SystemEndpointProfileService systemEndpointProfileService;
    private final AuditLedgerService auditLedgerService;
    private final PrivilegedAccessService privilegedAccessService;
    private final AccessGovernanceNotificationService accessGovernanceNotificationService;

    @Value("${app.security.api-keys.default-expiration-days:90}")
    private int defaultApiKeyExpirationDays;

    @Value("${app.security.api-keys.max-expiration-days:365}")
    private int maxApiKeyExpirationDays;

    @Transactional
    public TenantAccessCenterResponse getAccessCenter(String tenantId) {
        accessScopeService.ensureTenantHierarchy(tenantId, "system");
        TenantAuthConfigEntity authConfig = resolveAuthConfig(tenantId);
        List<TenantApiKey> apiKeys = tenantApiKeyRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId);
        List<User> users = userRepository.findByTenantId(tenantId);
        return TenantAccessCenterResponse.builder()
                .authConfig(toDto(authConfig))
                .governance(buildGovernance(authConfig, users, apiKeys))
                .oidcGroupRoleMappings(tenantOidcGroupRoleMappingRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId)
                        .stream()
                        .map(this::toDto)
                        .toList())
                .roles(roleRepository.findByTenantId(tenantId)
                        .stream()
                        .map(this::toDto)
                        .toList())
                .apiKeys(apiKeys.stream()
                        .map(this::toDto)
                        .toList())
                .emergencyAccessGrants(privilegedAccessService.listEmergencyAccessGrants(tenantId))
                .privilegedActionAlerts(privilegedAccessService.listPrivilegedActionAlerts(tenantId))
                .notificationHistory(accessGovernanceNotificationService.listRecentNotificationHistory(tenantId))
                .storeCatalog(accessScopeService.getTenantStoreCatalog(tenantId))
                .reconGroups(reconModuleService.getTenantReconGroups(tenantId))
                .systemEndpointProfiles(systemEndpointProfileService.getTenantProfiles(tenantId))
                .build();
    }

    @Transactional
    public QuarterlyAccessReviewCycleResponse startQuarterlyAccessReviewCycle(String tenantId,
                                                                              String actor) {
        accessScopeService.ensureTenantHierarchy(tenantId, actor);
        return privilegedAccessService.startQuarterlyAccessReviewCycle(tenantId, actor);
    }

    @Transactional
    public AccessGovernanceNotificationActionResultDto resendManagerAccessReviewReminder(String tenantId,
                                                                                         UUID userId,
                                                                                         String actor) {
        accessScopeService.ensureTenantHierarchy(tenantId, actor);
        AccessGovernanceNotificationActionResultDto result =
                accessGovernanceNotificationService.resendManagerAccessReviewReminder(tenantId, userId, actor);
        recordAudit(tenantId,
                "ACCESS_REVIEW_REMINDER_RESENT",
                "Manager access review reminder resent",
                userId.toString(),
                actor,
                null,
                result);
        return result;
    }

    @Transactional
    public AccessGovernanceNotificationActionResultDto escalateManagerAccessReview(String tenantId,
                                                                                   UUID userId,
                                                                                   String actor) {
        accessScopeService.ensureTenantHierarchy(tenantId, actor);
        AccessGovernanceNotificationActionResultDto result =
                accessGovernanceNotificationService.escalateManagerAccessReview(tenantId, userId, actor);
        recordAudit(tenantId,
                "ACCESS_REVIEW_ESCALATED",
                "Manager access review escalated",
                userId.toString(),
                actor,
                null,
                result);
        return result;
    }

    @Transactional
    public EmergencyAccessGrantDto grantEmergencyAccess(String tenantId,
                                                        GrantEmergencyAccessRequest request,
                                                        String actor) {
        accessScopeService.ensureTenantHierarchy(tenantId, actor);
        return privilegedAccessService.grantEmergencyAccess(tenantId, request, actor);
    }

    @Transactional
    public EmergencyAccessGrantDto revokeEmergencyAccess(String tenantId,
                                                         UUID grantId,
                                                         RevokeEmergencyAccessRequest request,
                                                         String actor) {
        accessScopeService.ensureTenantHierarchy(tenantId, actor);
        return privilegedAccessService.revokeEmergencyAccess(tenantId, grantId, request, actor);
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
        config.setOidcRedirectUri(trimToNull(safeRequest.getOidcRedirectUri()));
        config.setOidcScopes(normalizeScopes(safeRequest.getOidcScopes()));
        config.setOidcClientSecretRef(trimToNull(safeRequest.getOidcClientSecretRef()));
        if (safeRequest.getSamlEnabled() != null) {
            config.setSamlEnabled(safeRequest.getSamlEnabled());
        }
        config.setSamlDisplayName(trimToNull(safeRequest.getSamlDisplayName()));
        config.setSamlEntityId(trimToNull(safeRequest.getSamlEntityId()));
        config.setSamlAcsUrl(trimToNull(safeRequest.getSamlAcsUrl()));
        config.setSamlSsoUrl(trimToNull(safeRequest.getSamlSsoUrl()));
        config.setSamlIdpEntityId(trimToNull(safeRequest.getSamlIdpEntityId()));
        config.setSamlIdpMetadataUrl(trimToNull(safeRequest.getSamlIdpMetadataUrl()));
        config.setSamlIdpVerificationCertificate(trimToNull(safeRequest.getSamlIdpVerificationCertificate()));
        if (safeRequest.getApiKeyAuthEnabled() != null) {
            config.setApiKeyAuthEnabled(safeRequest.getApiKeyAuthEnabled());
        }
        if (safeRequest.getAutoProvisionUsers() != null) {
            config.setAutoProvisionUsers(safeRequest.getAutoProvisionUsers());
        }
        config.setAllowedEmailDomains(normalizeCsv(safeRequest.getAllowedEmailDomains(), true));
        config.setOidcUsernameClaim(defaultIfBlank(safeRequest.getOidcUsernameClaim(), "preferred_username"));
        config.setOidcEmailClaim(defaultIfBlank(safeRequest.getOidcEmailClaim(), "email"));
        config.setOidcGroupsClaim(defaultIfBlank(safeRequest.getOidcGroupsClaim(), "groups"));
        config.setSamlEmailAttribute(trimToNull(safeRequest.getSamlEmailAttribute()));
        config.setSamlGroupsAttribute(trimToNull(safeRequest.getSamlGroupsAttribute()));
        config.setSamlUsernameAttribute(defaultIfBlank(safeRequest.getSamlUsernameAttribute(), "uid"));
        if (safeRequest.getScimEnabled() != null) {
            config.setScimEnabled(safeRequest.getScimEnabled());
        }
        config.setScimBearerTokenRef(trimToNull(safeRequest.getScimBearerTokenRef()));
        if (safeRequest.getScimGroupPushEnabled() != null) {
            config.setScimGroupPushEnabled(safeRequest.getScimGroupPushEnabled());
        }
        config.setScimDeprovisionPolicy(defaultIfBlank(safeRequest.getScimDeprovisionPolicy(), "DEACTIVATE"));
        if (safeRequest.getManagerAccessReviewRemindersEnabled() != null) {
            config.setManagerAccessReviewRemindersEnabled(safeRequest.getManagerAccessReviewRemindersEnabled());
        }
        if (safeRequest.getManagerAccessReviewReminderIntervalDays() != null) {
            config.setManagerAccessReviewReminderIntervalDays(safeRequest.getManagerAccessReviewReminderIntervalDays());
        }
        if (safeRequest.getGovernanceNotificationMaxAttempts() != null) {
            config.setGovernanceNotificationMaxAttempts(safeRequest.getGovernanceNotificationMaxAttempts());
        }
        if (safeRequest.getGovernanceNotificationBackoffMinutes() != null) {
            config.setGovernanceNotificationBackoffMinutes(safeRequest.getGovernanceNotificationBackoffMinutes());
        }
        config.setManagerAccessReviewAdditionalEmails(normalizeCsv(safeRequest.getManagerAccessReviewAdditionalEmails(), true));
        config.setManagerAccessReviewTeamsWebhookUrl(trimToNull(safeRequest.getManagerAccessReviewTeamsWebhookUrl()));
        config.setManagerAccessReviewSlackWebhookUrl(trimToNull(safeRequest.getManagerAccessReviewSlackWebhookUrl()));
        if (safeRequest.getManagerAccessReviewEscalationEnabled() != null) {
            config.setManagerAccessReviewEscalationEnabled(safeRequest.getManagerAccessReviewEscalationEnabled());
        }
        if (safeRequest.getManagerAccessReviewEscalationAfterDays() != null) {
            config.setManagerAccessReviewEscalationAfterDays(safeRequest.getManagerAccessReviewEscalationAfterDays());
        }
        config.setManagerAccessReviewEscalationEmailRecipients(normalizeCsv(
                safeRequest.getManagerAccessReviewEscalationEmailRecipients(),
                true));
        config.setManagerAccessReviewEscalationTeamsWebhookUrl(trimToNull(
                safeRequest.getManagerAccessReviewEscalationTeamsWebhookUrl()));
        config.setManagerAccessReviewEscalationSlackWebhookUrl(trimToNull(
                safeRequest.getManagerAccessReviewEscalationSlackWebhookUrl()));
        if (safeRequest.getManagerAccessReviewNextTierEscalationEnabled() != null) {
            config.setManagerAccessReviewNextTierEscalationEnabled(
                    safeRequest.getManagerAccessReviewNextTierEscalationEnabled());
        }
        if (safeRequest.getManagerAccessReviewNextTierEscalationAfterDays() != null) {
            config.setManagerAccessReviewNextTierEscalationAfterDays(
                    safeRequest.getManagerAccessReviewNextTierEscalationAfterDays());
        }
        if (safeRequest.getPrivilegedActionAlertsEnabled() != null) {
            config.setPrivilegedActionAlertsEnabled(safeRequest.getPrivilegedActionAlertsEnabled());
        }
        config.setPrivilegedActionAlertEmailRecipients(normalizeCsv(safeRequest.getPrivilegedActionAlertEmailRecipients(), true));
        config.setPrivilegedActionAlertTeamsWebhookUrl(trimToNull(safeRequest.getPrivilegedActionAlertTeamsWebhookUrl()));
        config.setPrivilegedActionAlertSlackWebhookUrl(trimToNull(safeRequest.getPrivilegedActionAlertSlackWebhookUrl()));
        config.setManagerAccessReviewReminderSubjectTemplate(trimToNull(
                safeRequest.getManagerAccessReviewReminderSubjectTemplate()));
        config.setManagerAccessReviewReminderBodyTemplate(trimToNull(
                safeRequest.getManagerAccessReviewReminderBodyTemplate()));
        config.setManagerAccessReviewEscalationSubjectTemplate(trimToNull(
                safeRequest.getManagerAccessReviewEscalationSubjectTemplate()));
        config.setManagerAccessReviewEscalationBodyTemplate(trimToNull(
                safeRequest.getManagerAccessReviewEscalationBodyTemplate()));
        config.setPrivilegedActionAlertSubjectTemplate(trimToNull(
                safeRequest.getPrivilegedActionAlertSubjectTemplate()));
        config.setPrivilegedActionAlertBodyTemplate(trimToNull(
                safeRequest.getPrivilegedActionAlertBodyTemplate()));
        config.setUpdatedBy(defaultActor(actor));
        validateAuthConfig(config);

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
    public List<OidcGroupRoleMappingDto> saveOidcGroupRoleMappings(String tenantId,
                                                                   SaveOidcGroupRoleMappingsRequest request,
                                                                   String actor) {
        List<OidcGroupRoleMappingDto> before = tenantOidcGroupRoleMappingRepository
                .findByTenantIdOrderByUpdatedAtDesc(tenantId)
                .stream()
                .map(this::toDto)
                .toList();
        tenantOidcGroupRoleMappingRepository.deleteByTenantId(tenantId);
        tenantOidcGroupRoleMappingRepository.flush();

        List<OidcGroupRoleMappingAssignmentRequest> requested = request != null && request.getMappings() != null
                ? request.getMappings()
                : List.of();
        Set<String> seen = new LinkedHashSet<>();
        for (OidcGroupRoleMappingAssignmentRequest mappingRequest : requested) {
            String oidcGroup = trimToNull(mappingRequest != null ? mappingRequest.getOidcGroup() : null);
            if (oidcGroup == null) {
                continue;
            }
            if (mappingRequest.getRoleId() == null) {
                throw new IllegalArgumentException("Role is required for OIDC group " + oidcGroup);
            }
            Role role = roleRepository.findById(mappingRequest.getRoleId())
                    .filter(existing -> Objects.equals(existing.getTenantId(), tenantId))
                    .orElseThrow(() -> new IllegalArgumentException("Role not found for OIDC group " + oidcGroup));
            String key = oidcGroup.toLowerCase(Locale.ROOT) + "|" + role.getId();
            if (!seen.add(key)) {
                continue;
            }
            tenantOidcGroupRoleMappingRepository.save(TenantOidcGroupRoleMapping.builder()
                    .tenantId(tenantId)
                    .oidcGroup(oidcGroup)
                    .role(role)
                    .active(mappingRequest.getActive() == null || mappingRequest.getActive())
                    .updatedBy(defaultActor(actor))
                    .build());
        }

        List<OidcGroupRoleMappingDto> saved = tenantOidcGroupRoleMappingRepository
                .findByTenantIdOrderByUpdatedAtDesc(tenantId)
                .stream()
                .map(this::toDto)
                .toList();
        recordAudit(tenantId,
                "OIDC_GROUP_ROLE_MAPPINGS_UPDATED",
                "OIDC group role mappings updated",
                tenantId,
                actor,
                before,
                saved);
        return saved;
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
        int expirationDays = resolveApiKeyExpirationDays(safeRequest.getExpiresInDays());

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
                .expiresAt(LocalDateTime.now().plusDays(expirationDays))
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
        apiKey.setRevokedAt(LocalDateTime.now());
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
                .oidcRedirectUri(config.getOidcRedirectUri())
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
                        .oidcScopes("openid profile email")
                        .samlEnabled(false)
                        .samlUsernameAttribute("uid")
                        .apiKeyAuthEnabled(false)
                        .scimEnabled(false)
                        .scimGroupPushEnabled(false)
                        .scimDeprovisionPolicy("DEACTIVATE")
                        .managerAccessReviewRemindersEnabled(false)
                        .managerAccessReviewReminderIntervalDays(7)
                        .privilegedActionAlertsEnabled(false)
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
                .oidcRedirectUri(entity.getOidcRedirectUri())
                .oidcScopes(entity.getOidcScopes())
                .oidcClientSecretRef(entity.getOidcClientSecretRef())
                .samlEnabled(entity.isSamlEnabled())
                .samlDisplayName(entity.getSamlDisplayName())
                .samlEntityId(entity.getSamlEntityId())
                .samlAcsUrl(entity.getSamlAcsUrl())
                .samlSsoUrl(entity.getSamlSsoUrl())
                .samlIdpEntityId(entity.getSamlIdpEntityId())
                .samlIdpMetadataUrl(entity.getSamlIdpMetadataUrl())
                .samlIdpVerificationCertificate(entity.getSamlIdpVerificationCertificate())
                .apiKeyAuthEnabled(entity.isApiKeyAuthEnabled())
                .autoProvisionUsers(entity.isAutoProvisionUsers())
                .allowedEmailDomains(entity.getAllowedEmailDomains())
                .oidcUsernameClaim(entity.getOidcUsernameClaim())
                .oidcEmailClaim(entity.getOidcEmailClaim())
                .oidcGroupsClaim(entity.getOidcGroupsClaim())
                .samlEmailAttribute(entity.getSamlEmailAttribute())
                .samlGroupsAttribute(entity.getSamlGroupsAttribute())
                .samlUsernameAttribute(entity.getSamlUsernameAttribute())
                .scimEnabled(entity.isScimEnabled())
                .scimBearerTokenRef(entity.getScimBearerTokenRef())
                .scimGroupPushEnabled(entity.isScimGroupPushEnabled())
                .scimDeprovisionPolicy(entity.getScimDeprovisionPolicy())
                .managerAccessReviewRemindersEnabled(entity.isManagerAccessReviewRemindersEnabled())
                .managerAccessReviewReminderIntervalDays(entity.getManagerAccessReviewReminderIntervalDays())
                .governanceNotificationMaxAttempts(entity.getGovernanceNotificationMaxAttempts())
                .governanceNotificationBackoffMinutes(entity.getGovernanceNotificationBackoffMinutes())
                .managerAccessReviewAdditionalEmails(entity.getManagerAccessReviewAdditionalEmails())
                .managerAccessReviewTeamsWebhookUrl(entity.getManagerAccessReviewTeamsWebhookUrl())
                .managerAccessReviewSlackWebhookUrl(entity.getManagerAccessReviewSlackWebhookUrl())
                .managerAccessReviewEscalationEnabled(entity.isManagerAccessReviewEscalationEnabled())
                .managerAccessReviewEscalationAfterDays(entity.getManagerAccessReviewEscalationAfterDays())
                .managerAccessReviewEscalationEmailRecipients(entity.getManagerAccessReviewEscalationEmailRecipients())
                .managerAccessReviewEscalationTeamsWebhookUrl(entity.getManagerAccessReviewEscalationTeamsWebhookUrl())
                .managerAccessReviewEscalationSlackWebhookUrl(entity.getManagerAccessReviewEscalationSlackWebhookUrl())
                .managerAccessReviewNextTierEscalationEnabled(entity.isManagerAccessReviewNextTierEscalationEnabled())
                .managerAccessReviewNextTierEscalationAfterDays(entity.getManagerAccessReviewNextTierEscalationAfterDays())
                .privilegedActionAlertsEnabled(entity.isPrivilegedActionAlertsEnabled())
                .privilegedActionAlertEmailRecipients(entity.getPrivilegedActionAlertEmailRecipients())
                .privilegedActionAlertTeamsWebhookUrl(entity.getPrivilegedActionAlertTeamsWebhookUrl())
                .privilegedActionAlertSlackWebhookUrl(entity.getPrivilegedActionAlertSlackWebhookUrl())
                .managerAccessReviewReminderSubjectTemplate(entity.getManagerAccessReviewReminderSubjectTemplate())
                .managerAccessReviewReminderBodyTemplate(entity.getManagerAccessReviewReminderBodyTemplate())
                .managerAccessReviewEscalationSubjectTemplate(entity.getManagerAccessReviewEscalationSubjectTemplate())
                .managerAccessReviewEscalationBodyTemplate(entity.getManagerAccessReviewEscalationBodyTemplate())
                .privilegedActionAlertSubjectTemplate(entity.getPrivilegedActionAlertSubjectTemplate())
                .privilegedActionAlertBodyTemplate(entity.getPrivilegedActionAlertBodyTemplate())
                .updatedAt(entity.getUpdatedAt())
                .updatedBy(entity.getUpdatedBy())
                .build();
    }

    private OidcGroupRoleMappingDto toDto(TenantOidcGroupRoleMapping entity) {
        Role role = entity.getRole();
        return OidcGroupRoleMappingDto.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .oidcGroup(entity.getOidcGroup())
                .roleId(role != null ? role.getId() : null)
                .roleName(role != null ? role.getName() : null)
                .active(entity.isActive())
                .updatedAt(entity.getUpdatedAt())
                .updatedBy(entity.getUpdatedBy())
                .build();
    }

    private RoleDto toDto(Role role) {
        return RoleDto.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .permissionCodes(role.getPermissions().stream()
                        .map(permission -> permission.getCode())
                        .collect(java.util.stream.Collectors.toSet()))
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
                .expiresAt(entity.getExpiresAt())
                .revokedAt(entity.getRevokedAt())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private TenantAccessGovernanceResponse buildGovernance(TenantAuthConfigEntity authConfig,
                                                           List<User> users,
                                                           List<TenantApiKey> apiKeys) {
        LocalDateTime now = LocalDateTime.now();
        boolean ssoConfigured = authConfig.isOidcEnabled() || authConfig.isSamlEnabled();
        boolean ssoPreferred = ssoConfigured && !"LOCAL".equalsIgnoreCase(defaultIfBlank(authConfig.getPreferredLoginMode(), "LOCAL"));
        Map<UUID, User> usersById = new LinkedHashMap<>();
        Map<UUID, PrivilegedAccessService.ResolvedAccess> resolvedAccessByUserId = new LinkedHashMap<>();
        for (User user : users) {
            if (user.getId() != null) {
                usersById.put(user.getId(), user);
                resolvedAccessByUserId.put(user.getId(), privilegedAccessService.resolveEffectiveAccess(user));
            }
        }
        List<AccessGovernanceUserFindingDto> userFindings = users.stream()
                .map(user -> buildUserFinding(
                        user,
                        usersById.get(user.getManagerUserId()),
                        resolvedAccessByUserId.get(user.getId()),
                        ssoPreferred,
                        now))
                .filter(Objects::nonNull)
                .limit(50)
                .toList();
        List<AccessGovernanceApiKeyFindingDto> apiKeyFindings = apiKeys.stream()
                .map(apiKey -> buildApiKeyFinding(apiKey, now))
                .filter(Objects::nonNull)
                .limit(50)
                .toList();

        int highPrivilegeUsers = (int) users.stream()
                .filter(User::isActive)
                .filter(user -> privilegedAccessService.hasHighPrivilegeAccess(
                        resolvedAccessByUserId.getOrDefault(
                                user.getId(),
                                new PrivilegedAccessService.ResolvedAccess(Set.of(), Set.of(), List.of(), false, null))
                                .effectivePermissions()))
                .count();
        int usersWithoutRoles = (int) users.stream()
                .filter(user -> user.isActive() && (user.getRoles() == null || user.getRoles().isEmpty()))
                .count();
        int usersDueForReview = (int) users.stream()
                .filter(user -> user.isActive())
                .filter(user -> user.getAccessReviewDueAt() == null || !user.getAccessReviewDueAt().isAfter(now))
                .count();
        int usersPendingReview = (int) users.stream()
                .filter(user -> user.isActive())
                .filter(user -> "PENDING".equalsIgnoreCase(defaultIfBlank(user.getAccessReviewStatus(), "PENDING")))
                .count();
        int pendingManagerReviews = (int) users.stream()
                .filter(User::isActive)
                .filter(user -> "PENDING_MANAGER".equalsIgnoreCase(defaultIfBlank(user.getAccessReviewStatus(), "PENDING")))
                .count();
        int acknowledgedManagerReviews = (int) users.stream()
                .filter(User::isActive)
                .filter(user -> "PENDING_MANAGER".equalsIgnoreCase(defaultIfBlank(user.getAccessReviewStatus(), "PENDING")))
                .filter(this::hasAcknowledgedReminder)
                .count();
        int escalatedManagerReviews = (int) users.stream()
                .filter(User::isActive)
                .filter(user -> "PENDING_MANAGER".equalsIgnoreCase(defaultIfBlank(user.getAccessReviewStatus(), "PENDING")))
                .filter(this::hasEscalatedReminder)
                .count();
        int nextTierEscalatedManagerReviews = (int) users.stream()
                .filter(User::isActive)
                .filter(user -> "PENDING_MANAGER".equalsIgnoreCase(defaultIfBlank(user.getAccessReviewStatus(), "PENDING")))
                .filter(this::hasNextTierEscalatedReminder)
                .count();
        int usersWithoutManager = (int) users.stream()
                .filter(User::isActive)
                .filter(user -> user.getManagerUserId() == null)
                .count();
        int usersMissingExternalSubject = (int) users.stream()
                .filter(user -> user.isActive())
                .filter(user -> !"LOCAL".equalsIgnoreCase(defaultIfBlank(user.getIdentityProvider(), "LOCAL")))
                .filter(user -> trimToNull(user.getExternalSubject()) == null)
                .count();
        int localIdentityUsers = (int) users.stream()
                .filter(user -> "LOCAL".equalsIgnoreCase(defaultIfBlank(user.getIdentityProvider(), "LOCAL")))
                .count();
        int activeApiKeys = (int) apiKeys.stream().filter(TenantApiKey::isActive).count();
        int expiredApiKeys = (int) apiKeys.stream()
                .filter(TenantApiKey::isActive)
                .filter(apiKey -> apiKey.getExpiresAt() != null && !apiKey.getExpiresAt().isAfter(now))
                .count();
        int apiKeysExpiringSoon = (int) apiKeys.stream()
                .filter(TenantApiKey::isActive)
                .filter(apiKey -> apiKey.getExpiresAt() != null
                        && apiKey.getExpiresAt().isAfter(now)
                        && !apiKey.getExpiresAt().isAfter(now.plusDays(30)))
                .count();
        int activeEmergencyAccessUsers = (int) users.stream()
                .filter(User::isActive)
                .filter(user -> resolvedAccessByUserId.getOrDefault(
                                user.getId(),
                                new PrivilegedAccessService.ResolvedAccess(Set.of(), Set.of(), List.of(), false, null))
                        .emergencyAccessActive())
                .count();

        return TenantAccessGovernanceResponse.builder()
                .totalUsers(users.size())
                .activeUsers((int) users.stream().filter(User::isActive).count())
                .inactiveUsers((int) users.stream().filter(user -> !user.isActive()).count())
                .localIdentityUsers(localIdentityUsers)
                .externalIdentityUsers(users.size() - localIdentityUsers)
                .usersDueForReview(usersDueForReview)
                .usersPendingReview(usersPendingReview)
                .pendingManagerReviews(pendingManagerReviews)
                .acknowledgedManagerReviews(acknowledgedManagerReviews)
                .escalatedManagerReviews(escalatedManagerReviews)
                .nextTierEscalatedManagerReviews(nextTierEscalatedManagerReviews)
                .usersWithoutManager(usersWithoutManager)
                .highPrivilegeUsers(highPrivilegeUsers)
                .activeEmergencyAccessUsers(activeEmergencyAccessUsers)
                .usersWithoutRoles(usersWithoutRoles)
                .usersMissingExternalSubject(usersMissingExternalSubject)
                .activeApiKeys(activeApiKeys)
                .apiKeysExpiringSoon(apiKeysExpiringSoon)
                .expiredApiKeys(expiredApiKeys)
                .ssoConfigured(ssoConfigured)
                .ssoPreferred(ssoPreferred)
                .preferredLoginMode(defaultIfBlank(authConfig.getPreferredLoginMode(), "LOCAL"))
                .userFindings(userFindings)
                .apiKeyFindings(apiKeyFindings)
                .build();
    }

    private AccessGovernanceUserFindingDto buildUserFinding(User user,
                                                            User manager,
                                                            PrivilegedAccessService.ResolvedAccess resolvedAccess,
                                                            boolean ssoPreferred,
                                                            LocalDateTime now) {
        List<String> findings = new ArrayList<>();
        String identityProvider = defaultIfBlank(user.getIdentityProvider(), "LOCAL").toUpperCase(Locale.ROOT);
        PrivilegedAccessService.ResolvedAccess safeResolvedAccess = resolvedAccess != null
                ? resolvedAccess
                : new PrivilegedAccessService.ResolvedAccess(Set.of(), Set.of(), List.of(), false, null);
        if (user.isActive() && (user.getAccessReviewDueAt() == null || !user.getAccessReviewDueAt().isAfter(now))) {
            findings.add("ACCESS_REVIEW_DUE");
        }
        if (user.isActive() && "PENDING".equalsIgnoreCase(defaultIfBlank(user.getAccessReviewStatus(), "PENDING"))) {
            findings.add("ACCESS_REVIEW_PENDING");
        }
        if (user.isActive() && "PENDING_MANAGER".equalsIgnoreCase(defaultIfBlank(user.getAccessReviewStatus(), "PENDING"))) {
            findings.add("MANAGER_REVIEW_PENDING");
        }
        if (user.isActive()
                && "PENDING_MANAGER".equalsIgnoreCase(defaultIfBlank(user.getAccessReviewStatus(), "PENDING"))
                && hasAcknowledgedReminder(user)) {
            findings.add("REVIEW_REMINDER_ACKNOWLEDGED");
        }
        if (user.isActive()
                && "PENDING_MANAGER".equalsIgnoreCase(defaultIfBlank(user.getAccessReviewStatus(), "PENDING"))
                && hasEscalatedReminder(user)) {
            findings.add("REVIEW_REMINDER_ESCALATED");
        }
        if (user.isActive()
                && "PENDING_MANAGER".equalsIgnoreCase(defaultIfBlank(user.getAccessReviewStatus(), "PENDING"))
                && hasNextTierEscalatedReminder(user)) {
            findings.add("REVIEW_NEXT_TIER_ESCALATED");
        }
        if (user.isActive() && privilegedAccessService.hasHighPrivilegeAccess(safeResolvedAccess.effectivePermissions())) {
            findings.add("HIGH_PRIVILEGE_ACCESS");
        }
        if (user.isActive() && (user.getRoles() == null || user.getRoles().isEmpty())) {
            findings.add("NO_ROLE_ASSIGNED");
        }
        if (user.isActive() && user.getManagerUserId() == null) {
            findings.add("MANAGER_NOT_ASSIGNED");
        }
        if (user.isActive()
                && !"LOCAL".equals(identityProvider)
                && trimToNull(user.getExternalSubject()) == null) {
            findings.add("EXTERNAL_SUBJECT_MISSING");
        }
        if (user.isActive() && ssoPreferred && "LOCAL".equals(identityProvider)) {
            findings.add("LOCAL_IDENTITY_WHILE_SSO_PREFERRED");
        }
        if (user.isActive()
                && user.getLastLogin() != null
                && user.getLastLogin().isBefore(now.minusDays(90))) {
            findings.add("DORMANT_USER");
        }
        if (user.isActive() && safeResolvedAccess.emergencyAccessActive()) {
            findings.add("EMERGENCY_ACCESS_ACTIVE");
        }
        if (findings.isEmpty()) {
            return null;
        }

        List<String> roleNames = safeResolvedAccess.effectiveRoles().stream()
                .map(role -> defaultIfBlank(role.getName(), ""))
                .filter(name -> !name.isBlank())
                .sorted()
                .toList();
        List<String> permissionCodes = safeResolvedAccess.effectivePermissions().stream()
                .sorted()
                .toList();

        return AccessGovernanceUserFindingDto.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .active(user.isActive())
                .identityProvider(identityProvider)
                .externalSubject(user.getExternalSubject())
                .managerUserId(user.getManagerUserId())
                .managerUsername(manager != null ? manager.getUsername() : null)
                .managerFullName(manager != null ? firstNonBlank(manager.getFullName(), manager.getUsername()) : null)
                .accessReviewStatus(defaultIfBlank(user.getAccessReviewStatus(), "PENDING"))
                .accessReviewDueAt(user.getAccessReviewDueAt())
                .lastAccessReviewAt(user.getLastAccessReviewAt())
                .lastAccessReviewBy(user.getLastAccessReviewBy())
                .accessReviewLastReminderAt(user.getAccessReviewLastReminderAt())
                .accessReviewReminderAcknowledgedAt(user.getAccessReviewReminderAcknowledgedAt())
                .accessReviewReminderAcknowledgedBy(user.getAccessReviewReminderAcknowledgedBy())
                .accessReviewLastEscalatedAt(user.getAccessReviewLastEscalatedAt())
                .accessReviewLastNextTierEscalatedAt(user.getAccessReviewLastNextTierEscalatedAt())
                .lastLogin(user.getLastLogin())
                .emergencyAccessActive(safeResolvedAccess.emergencyAccessActive())
                .emergencyAccessExpiresAt(safeResolvedAccess.emergencyAccessExpiresAt())
                .roleNames(roleNames)
                .permissionCodes(permissionCodes)
                .findingTypes(findings)
                .build();
    }

    private AccessGovernanceApiKeyFindingDto buildApiKeyFinding(TenantApiKey apiKey,
                                                                LocalDateTime now) {
        if (!apiKey.isActive()) {
            return null;
        }
        List<String> findings = new ArrayList<>();
        if (apiKey.getExpiresAt() == null) {
            findings.add("NO_EXPIRATION");
        } else if (!apiKey.getExpiresAt().isAfter(now)) {
            findings.add("EXPIRED");
        } else if (!apiKey.getExpiresAt().isAfter(now.plusDays(30))) {
            findings.add("EXPIRING_SOON");
        }
        if (apiKey.isAllStoreAccess()) {
            findings.add("ALL_STORE_SCOPE");
        }
        if (findings.isEmpty()) {
            return null;
        }
        return AccessGovernanceApiKeyFindingDto.builder()
                .id(apiKey.getId())
                .keyName(apiKey.getKeyName())
                .keyPrefix(apiKey.getKeyPrefix())
                .active(apiKey.isActive())
                .expiresAt(apiKey.getExpiresAt())
                .lastUsedAt(apiKey.getLastUsedAt())
                .findingTypes(findings)
                .build();
    }

    private boolean hasHighPrivilegeAccess(User user) {
        return user.getAllPermissions().stream().anyMatch(this::isHighPrivilegePermission);
    }

    private boolean isHighPrivilegePermission(String permission) {
        String normalized = normalizeUpper(permission);
        return normalized != null
                && (normalized.startsWith("ADMIN_")
                || normalized.endsWith("_MANAGE")
                || Set.of("AUDIT_EXPORT", "AUDIT_GLOBAL_VIEW", "API_ACCESS_MANAGE").contains(normalized));
    }

    private int resolveApiKeyExpirationDays(Integer requestedDays) {
        int fallback = Math.max(1, defaultApiKeyExpirationDays);
        int max = Math.max(fallback, maxApiKeyExpirationDays);
        int resolved = requestedDays == null ? fallback : requestedDays;
        if (resolved <= 0) {
            throw new IllegalArgumentException("API key expiration must be at least 1 day");
        }
        if (resolved > max) {
            throw new IllegalArgumentException("API key expiration must not exceed " + max + " days");
        }
        return resolved;
    }

    private void validateAuthConfig(TenantAuthConfigEntity config) {
        String preferredMode = normalizePreferredLoginMode(config.getPreferredLoginMode());
        config.setPreferredLoginMode(preferredMode);

        if (!config.isLocalLoginEnabled() && !config.isOidcEnabled() && !config.isSamlEnabled()) {
            throw new IllegalArgumentException("At least one interactive login mode must be enabled");
        }
        if ("LOCAL".equals(preferredMode) && !config.isLocalLoginEnabled()) {
            throw new IllegalArgumentException("Preferred login mode LOCAL requires local login to be enabled");
        }
        if ("OIDC".equals(preferredMode) && !config.isOidcEnabled()) {
            throw new IllegalArgumentException("Preferred login mode OIDC requires OIDC to be enabled");
        }
        if ("SAML".equals(preferredMode) && !config.isSamlEnabled()) {
            throw new IllegalArgumentException("Preferred login mode SAML requires SAML to be enabled");
        }
        if (config.isOidcEnabled()) {
            requireHttpUrl(config.getOidcIssuerUrl(), "OIDC issuer URL");
            requireField(config.getOidcClientId(), "OIDC client id");
            requireHttpUrl(config.getOidcRedirectUri(), "OIDC redirect URI");
            if (!splitScopes(config.getOidcScopes()).contains("openid")) {
                throw new IllegalArgumentException("OIDC scopes must include openid");
            }
        }
        if (config.isSamlEnabled()) {
            requireField(config.getSamlEntityId(), "SAML entity id");
            requireHttpUrl(config.getSamlAcsUrl(), "SAML ACS URL");
            if (trimToNull(config.getSamlIdpMetadataUrl()) != null) {
                requireHttpUrl(config.getSamlIdpMetadataUrl(), "SAML IdP metadata URL");
            } else {
                requireField(config.getSamlIdpEntityId(), "SAML IdP entity id");
                requireHttpUrl(config.getSamlSsoUrl(), "SAML SSO URL");
                requireField(config.getSamlIdpVerificationCertificate(), "SAML IdP verification certificate");
            }
        }
        if (config.isAutoProvisionUsers()) {
            if (!config.isOidcEnabled() && !config.isSamlEnabled()) {
                throw new IllegalArgumentException("Auto provisioning requires OIDC or SAML to be enabled");
            }
            requireField(config.getAllowedEmailDomains(), "Allowed email domains");
        }
        if (config.isScimEnabled()) {
            requireField(config.getScimBearerTokenRef(), "SCIM bearer token reference");
            String policy = defaultIfBlank(config.getScimDeprovisionPolicy(), "DEACTIVATE").toUpperCase(Locale.ROOT);
            if (!Set.of("DEACTIVATE", "REMOVE_ACCESS").contains(policy)) {
                throw new IllegalArgumentException("SCIM deprovision policy must be DEACTIVATE or REMOVE_ACCESS");
            }
            config.setScimDeprovisionPolicy(policy);
            if (config.isScimGroupPushEnabled() && !config.isScimEnabled()) {
                throw new IllegalArgumentException("SCIM group push requires SCIM provisioning to be enabled");
            }
        } else if (config.isScimGroupPushEnabled()) {
            throw new IllegalArgumentException("SCIM group push requires SCIM provisioning to be enabled");
        }
        int reminderIntervalDays = config.getManagerAccessReviewReminderIntervalDays();
        if (reminderIntervalDays < 1 || reminderIntervalDays > 30) {
            throw new IllegalArgumentException("Manager access review reminder interval must be between 1 and 30 days");
        }
        int maxAttempts = config.getGovernanceNotificationMaxAttempts();
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException("Governance notification max attempts must be between 1 and 10");
        }
        int backoffMinutes = config.getGovernanceNotificationBackoffMinutes();
        if (backoffMinutes < 1 || backoffMinutes > 1440) {
            throw new IllegalArgumentException("Governance notification backoff must be between 1 and 1440 minutes");
        }
        int escalationAfterDays = config.getManagerAccessReviewEscalationAfterDays();
        if (escalationAfterDays < 1 || escalationAfterDays > 30) {
            throw new IllegalArgumentException("Manager access review escalation must be between 1 and 30 days");
        }
        if (config.isManagerAccessReviewEscalationEnabled() && !config.isManagerAccessReviewRemindersEnabled()) {
            throw new IllegalArgumentException("Manager access review escalation requires reminders to be enabled");
        }
        int nextTierEscalationAfterDays = config.getManagerAccessReviewNextTierEscalationAfterDays();
        if (nextTierEscalationAfterDays < 1 || nextTierEscalationAfterDays > 30) {
            throw new IllegalArgumentException("Next-tier manager escalation must be between 1 and 30 days");
        }
        if (config.isManagerAccessReviewNextTierEscalationEnabled() && !config.isManagerAccessReviewRemindersEnabled()) {
            throw new IllegalArgumentException("Next-tier manager escalation requires reminders to be enabled");
        }
        requireHttpUrlIfPresent(config.getManagerAccessReviewTeamsWebhookUrl(), "Manager access review Teams webhook URL");
        requireHttpUrlIfPresent(config.getManagerAccessReviewSlackWebhookUrl(), "Manager access review Slack webhook URL");
        requireHttpUrlIfPresent(config.getManagerAccessReviewEscalationTeamsWebhookUrl(), "Manager access review escalation Teams webhook URL");
        requireHttpUrlIfPresent(config.getManagerAccessReviewEscalationSlackWebhookUrl(), "Manager access review escalation Slack webhook URL");
        requireHttpUrlIfPresent(config.getPrivilegedActionAlertTeamsWebhookUrl(), "Privileged action alert Teams webhook URL");
        requireHttpUrlIfPresent(config.getPrivilegedActionAlertSlackWebhookUrl(), "Privileged action alert Slack webhook URL");
    }

    private String normalizePreferredLoginMode(String value) {
        String mode = defaultIfBlank(value, "LOCAL").toUpperCase(Locale.ROOT);
        if (!Set.of("LOCAL", "OIDC", "SAML").contains(mode)) {
            throw new IllegalArgumentException("Preferred login mode must be LOCAL, OIDC, or SAML");
        }
        return mode;
    }

    private void requireField(String value, String label) {
        if (trimToNull(value) == null) {
            throw new IllegalArgumentException(label + " is required");
        }
    }

    private void requireHttpUrl(String value, String label) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (uri.getHost() == null
                    || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException(label + " must be an HTTP or HTTPS URL");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(label + " must be a valid URL");
        }
    }

    private void requireHttpUrlIfPresent(String value, String label) {
        if (trimToNull(value) != null) {
            requireHttpUrl(value, label);
        }
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

    private String normalizeCsv(String value, boolean lowerCase) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return java.util.Arrays.stream(trimmed.split(","))
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .map(item -> lowerCase ? item.toLowerCase(Locale.ROOT) : item)
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));
    }

    private String normalizeScopes(String value) {
        List<String> scopes = splitScopes(defaultIfBlank(value, "openid profile email"));
        if (scopes.isEmpty()) {
            scopes = List.of("openid", "profile", "email");
        }
        if (!scopes.contains("openid")) {
            List<String> withOpenId = new ArrayList<>();
            withOpenId.add("openid");
            withOpenId.addAll(scopes);
            scopes = withOpenId;
        }
        return String.join(" ", scopes);
    }

    private List<String> splitScopes(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return List.of();
        }
        return java.util.Arrays.stream(trimmed.split("[,\\s]+"))
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .map(item -> item.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String defaultActor(String actor) {
        String trimmed = trimToNull(actor);
        return trimmed == null ? "system" : trimmed;
    }

    private boolean hasAcknowledgedReminder(User user) {
        if (user == null || user.getAccessReviewReminderAcknowledgedAt() == null) {
            return false;
        }
        return user.getAccessReviewLastReminderAt() == null
                || !user.getAccessReviewReminderAcknowledgedAt().isBefore(user.getAccessReviewLastReminderAt());
    }

    private boolean hasEscalatedReminder(User user) {
        if (user == null) {
            return false;
        }
        LocalDateTime lastReminderAt = user.getAccessReviewLastReminderAt();
        boolean adminEscalated = user.getAccessReviewLastEscalatedAt() != null
                && (lastReminderAt == null || !user.getAccessReviewLastEscalatedAt().isBefore(lastReminderAt));
        boolean nextTierEscalated = user.getAccessReviewLastNextTierEscalatedAt() != null
                && (lastReminderAt == null || !user.getAccessReviewLastNextTierEscalatedAt().isBefore(lastReminderAt));
        return adminEscalated || nextTierEscalated;
    }

    private boolean hasNextTierEscalatedReminder(User user) {
        if (user == null || user.getAccessReviewLastNextTierEscalatedAt() == null) {
            return false;
        }
        return user.getAccessReviewLastReminderAt() == null
                || !user.getAccessReviewLastNextTierEscalatedAt().isBefore(user.getAccessReviewLastReminderAt());
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
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
                .oidcRedirectUri(entity.getOidcRedirectUri())
                .oidcScopes(entity.getOidcScopes())
                .oidcClientSecretRef(entity.getOidcClientSecretRef())
                .samlEnabled(entity.isSamlEnabled())
                .samlDisplayName(entity.getSamlDisplayName())
                .samlEntityId(entity.getSamlEntityId())
                .samlAcsUrl(entity.getSamlAcsUrl())
                .samlSsoUrl(entity.getSamlSsoUrl())
                .samlIdpEntityId(entity.getSamlIdpEntityId())
                .samlIdpMetadataUrl(entity.getSamlIdpMetadataUrl())
                .samlIdpVerificationCertificate(entity.getSamlIdpVerificationCertificate())
                .apiKeyAuthEnabled(entity.isApiKeyAuthEnabled())
                .autoProvisionUsers(entity.isAutoProvisionUsers())
                .allowedEmailDomains(entity.getAllowedEmailDomains())
                .oidcUsernameClaim(entity.getOidcUsernameClaim())
                .oidcEmailClaim(entity.getOidcEmailClaim())
                .oidcGroupsClaim(entity.getOidcGroupsClaim())
                .samlEmailAttribute(entity.getSamlEmailAttribute())
                .samlGroupsAttribute(entity.getSamlGroupsAttribute())
                .samlUsernameAttribute(entity.getSamlUsernameAttribute())
                .scimEnabled(entity.isScimEnabled())
                .scimBearerTokenRef(entity.getScimBearerTokenRef())
                .scimGroupPushEnabled(entity.isScimGroupPushEnabled())
                .scimDeprovisionPolicy(entity.getScimDeprovisionPolicy())
                .managerAccessReviewRemindersEnabled(entity.isManagerAccessReviewRemindersEnabled())
                .managerAccessReviewReminderIntervalDays(entity.getManagerAccessReviewReminderIntervalDays())
                .governanceNotificationMaxAttempts(entity.getGovernanceNotificationMaxAttempts())
                .governanceNotificationBackoffMinutes(entity.getGovernanceNotificationBackoffMinutes())
                .managerAccessReviewAdditionalEmails(entity.getManagerAccessReviewAdditionalEmails())
                .managerAccessReviewTeamsWebhookUrl(entity.getManagerAccessReviewTeamsWebhookUrl())
                .managerAccessReviewSlackWebhookUrl(entity.getManagerAccessReviewSlackWebhookUrl())
                .managerAccessReviewEscalationEnabled(entity.isManagerAccessReviewEscalationEnabled())
                .managerAccessReviewEscalationAfterDays(entity.getManagerAccessReviewEscalationAfterDays())
                .managerAccessReviewEscalationEmailRecipients(entity.getManagerAccessReviewEscalationEmailRecipients())
                .managerAccessReviewEscalationTeamsWebhookUrl(entity.getManagerAccessReviewEscalationTeamsWebhookUrl())
                .managerAccessReviewEscalationSlackWebhookUrl(entity.getManagerAccessReviewEscalationSlackWebhookUrl())
                .managerAccessReviewNextTierEscalationEnabled(entity.isManagerAccessReviewNextTierEscalationEnabled())
                .managerAccessReviewNextTierEscalationAfterDays(entity.getManagerAccessReviewNextTierEscalationAfterDays())
                .privilegedActionAlertsEnabled(entity.isPrivilegedActionAlertsEnabled())
                .privilegedActionAlertEmailRecipients(entity.getPrivilegedActionAlertEmailRecipients())
                .privilegedActionAlertTeamsWebhookUrl(entity.getPrivilegedActionAlertTeamsWebhookUrl())
                .privilegedActionAlertSlackWebhookUrl(entity.getPrivilegedActionAlertSlackWebhookUrl())
                .managerAccessReviewReminderSubjectTemplate(entity.getManagerAccessReviewReminderSubjectTemplate())
                .managerAccessReviewReminderBodyTemplate(entity.getManagerAccessReviewReminderBodyTemplate())
                .managerAccessReviewEscalationSubjectTemplate(entity.getManagerAccessReviewEscalationSubjectTemplate())
                .managerAccessReviewEscalationBodyTemplate(entity.getManagerAccessReviewEscalationBodyTemplate())
                .privilegedActionAlertSubjectTemplate(entity.getPrivilegedActionAlertSubjectTemplate())
                .privilegedActionAlertBodyTemplate(entity.getPrivilegedActionAlertBodyTemplate())
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
                .expiresAt(entity.getExpiresAt())
                .revokedAt(entity.getRevokedAt())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
