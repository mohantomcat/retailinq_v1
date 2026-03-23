package com.recon.api.service;

import com.recon.api.domain.AccessScopeSummaryDto;
import com.recon.api.domain.OrganizationUnit;
import com.recon.api.domain.OrganizationUnitDto;
import com.recon.api.domain.User;
import com.recon.api.domain.UserOrganizationScope;
import com.recon.api.domain.UserOrganizationScopeDto;
import com.recon.api.repository.ExceptionCaseRepository;
import com.recon.api.repository.OrganizationUnitRepository;
import com.recon.api.repository.UserOrganizationScopeRepository;
import com.recon.api.repository.UserRepository;
import com.recon.api.security.ReconUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccessScopeService {

    private static final String ROOT_UNIT_KEY = "ROOT";

    private final OrganizationUnitRepository organizationUnitRepository;
    private final UserOrganizationScopeRepository userOrganizationScopeRepository;
    private final UserRepository userRepository;
    private final ExceptionCaseRepository exceptionCaseRepository;

    @Transactional
    public void ensureTenantHierarchy(String tenantId, String actor) {
        if (trimToNull(tenantId) == null) {
            return;
        }

        OrganizationUnit root = organizationUnitRepository.findByTenantIdAndUnitKeyIgnoreCase(tenantId, ROOT_UNIT_KEY)
                .orElseGet(() -> organizationUnitRepository.save(OrganizationUnit.builder()
                        .tenantId(tenantId)
                        .unitKey(ROOT_UNIT_KEY)
                        .unitName("HQ")
                        .unitType("ORGANIZATION")
                        .sortOrder(0)
                        .active(true)
                        .createdBy(defaultActor(actor))
                        .updatedBy(defaultActor(actor))
                        .build()));

        List<String> storeCatalog = new ArrayList<>();
        storeCatalog.addAll(userRepository.findDistinctStoreIdsByTenantId(tenantId));
        storeCatalog.addAll(exceptionCaseRepository.findDistinctStoreIdsByTenantId(tenantId));

        normalizeStoreCollection(storeCatalog).forEach(storeId -> {
            if (organizationUnitRepository.findByTenantIdAndStoreIdAndActiveTrue(tenantId, storeId).isEmpty()) {
                organizationUnitRepository.save(OrganizationUnit.builder()
                        .tenantId(tenantId)
                        .unitKey("STORE-" + storeId)
                        .unitName("Store " + storeId)
                        .unitType("STORE")
                        .parentUnit(root)
                        .storeId(storeId)
                        .sortOrder(100)
                        .active(true)
                        .createdBy(defaultActor(actor))
                        .updatedBy(defaultActor(actor))
                        .build());
            }
        });
    }

    @Transactional(readOnly = true)
    public AccessScopeSummaryDto summarizeUserScope(User user) {
        if (user == null) {
            return AccessScopeSummaryDto.builder()
                    .allStoreAccess(true)
                    .directStoreIds(List.of())
                    .effectiveStoreIds(List.of())
                    .organizationScopes(List.of())
                    .accessLabel("All stores")
                    .build();
        }

        List<String> directStoreIds = normalizeStoreCollection(user.getStoreIds());
        List<UserOrganizationScope> orgScopes = loadUserOrganizationScopes(user.getTenantId(), user.getId());
        Set<String> scopedStoreIds = resolveStoreIdsFromOrganizationScopes(user.getTenantId(), orgScopes);
        boolean allStoreAccess = directStoreIds.isEmpty() && orgScopes.isEmpty();

        Set<String> effectiveStoreIds = new LinkedHashSet<>(directStoreIds);
        effectiveStoreIds.addAll(scopedStoreIds);

        return AccessScopeSummaryDto.builder()
                .allStoreAccess(allStoreAccess)
                .directStoreIds(directStoreIds)
                .effectiveStoreIds(new ArrayList<>(effectiveStoreIds))
                .organizationScopes(toScopeDtos(orgScopes))
                .accessLabel(allStoreAccess
                        ? "All stores"
                        : effectiveStoreIds.size() == 1
                        ? "1 store"
                        : effectiveStoreIds.size() + " stores")
                .build();
    }

    @Transactional(readOnly = true)
    public Set<String> resolveEffectiveStoreIds(User user) {
        AccessScopeSummaryDto summary = summarizeUserScope(user);
        if (summary.isAllStoreAccess()) {
            return new LinkedHashSet<>(getTenantStoreCatalog(user.getTenantId()));
        }
        return new LinkedHashSet<>(summary.getEffectiveStoreIds());
    }

    @Transactional(readOnly = true)
    public List<UserOrganizationScope> loadUserOrganizationScopes(String tenantId, UUID userId) {
        if (trimToNull(tenantId) == null || userId == null) {
            return List.of();
        }
        return userOrganizationScopeRepository.findByTenantIdAndUser_Id(tenantId, userId);
    }

    @Transactional(readOnly = true)
    public List<String> getTenantStoreCatalog(String tenantId) {
        if (trimToNull(tenantId) == null) {
            return List.of();
        }
        List<String> storeIds = new ArrayList<>();
        storeIds.addAll(organizationUnitRepository
                .findByTenantIdAndUnitTypeIgnoreCaseAndActiveTrueOrderBySortOrderAscUnitNameAsc(tenantId, "STORE")
                .stream()
                .map(OrganizationUnit::getStoreId)
                .toList());
        storeIds.addAll(userRepository.findDistinctStoreIdsByTenantId(tenantId));
        storeIds.addAll(exceptionCaseRepository.findDistinctStoreIdsByTenantId(tenantId));
        return normalizeStoreCollection(storeIds);
    }

    @Transactional(readOnly = true)
    public List<OrganizationUnitDto> getOrganizationUnits(String tenantId) {
        List<OrganizationUnit> units = organizationUnitRepository.findByTenantIdOrderBySortOrderAscUnitNameAsc(tenantId);
        Map<UUID, Long> childCounts = units.stream()
                .filter(unit -> unit.getParentUnit() != null)
                .collect(Collectors.groupingBy(unit -> unit.getParentUnit().getId(), Collectors.counting()));

        return units.stream()
                .sorted(Comparator
                        .comparing((OrganizationUnit unit) -> unit.getParentUnit() == null ? 0 : 1)
                        .thenComparing(OrganizationUnit::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(OrganizationUnit::getUnitName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(unit -> OrganizationUnitDto.builder()
                        .id(unit.getId())
                        .tenantId(unit.getTenantId())
                        .unitKey(unit.getUnitKey())
                        .unitName(unit.getUnitName())
                        .unitType(unit.getUnitType())
                        .parentUnitId(unit.getParentUnit() != null ? unit.getParentUnit().getId() : null)
                        .parentUnitKey(unit.getParentUnit() != null ? unit.getParentUnit().getUnitKey() : null)
                        .parentUnitName(unit.getParentUnit() != null ? unit.getParentUnit().getUnitName() : null)
                        .storeId(unit.getStoreId())
                        .sortOrder(unit.getSortOrder())
                        .active(unit.isActive())
                        .childCount(childCounts.getOrDefault(unit.getId(), 0L))
                        .assignedUserCount(userOrganizationScopeRepository.countByTenantIdAndOrganizationUnit_Id(tenantId, unit.getId()))
                        .createdAt(unit.getCreatedAt())
                        .updatedAt(unit.getUpdatedAt())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public StoreScopeFilter resolveStoreScope(ReconUserPrincipal principal, Collection<String> requestedStoreIds) {
        List<String> requested = normalizeStoreCollection(requestedStoreIds);
        if (principal == null) {
            return new StoreScopeFilter(false, requested);
        }

        if (principal.isAllStoreAccess()) {
            List<String> tenantStores = getTenantStoreCatalog(principal.getTenantId());
            if (!requested.isEmpty()) {
                if (tenantStores.isEmpty()) {
                    return new StoreScopeFilter(true, List.of());
                }
                List<String> filtered = requested.stream()
                        .filter(storeId -> tenantStores.contains(storeId))
                        .toList();
                return new StoreScopeFilter(filtered.isEmpty(), filtered);
            }
            return new StoreScopeFilter(tenantStores.isEmpty(), tenantStores);
        }

        List<String> allowed = normalizeStoreCollection(principal.getStoreIds());
        if (allowed.isEmpty()) {
            return new StoreScopeFilter(true, List.of());
        }
        if (requested.isEmpty()) {
            return new StoreScopeFilter(false, allowed);
        }
        List<String> filtered = requested.stream()
                .filter(allowed::contains)
                .toList();
        return new StoreScopeFilter(filtered.isEmpty(), filtered);
    }

    @Transactional(readOnly = true)
    public boolean canAccessStore(ReconUserPrincipal principal, String storeId) {
        String normalizedStoreId = normalizeStore(storeId);
        if (normalizedStoreId == null) {
            return true;
        }
        return principal != null && principal.canAccessStore(normalizedStoreId);
    }

    @Transactional(readOnly = true)
    public Set<String> resolveStoreIdsFromOrganizationScopes(String tenantId, List<UserOrganizationScope> scopes) {
        if (trimToNull(tenantId) == null || scopes == null || scopes.isEmpty()) {
            return Set.of();
        }
        List<OrganizationUnit> units = organizationUnitRepository.findByTenantIdOrderBySortOrderAscUnitNameAsc(tenantId);
        Map<UUID, OrganizationUnit> byId = units.stream()
                .collect(Collectors.toMap(OrganizationUnit::getId, unit -> unit, (left, right) -> left, LinkedHashMap::new));
        Map<UUID, List<OrganizationUnit>> childrenByParent = units.stream()
                .filter(unit -> unit.getParentUnit() != null)
                .collect(Collectors.groupingBy(unit -> unit.getParentUnit().getId()));

        Set<String> stores = new LinkedHashSet<>();
        for (UserOrganizationScope scope : scopes) {
            OrganizationUnit baseUnit = scope.getOrganizationUnit();
            if (baseUnit == null) {
                baseUnit = byId.get(scope.getOrganizationUnit() != null ? scope.getOrganizationUnit().getId() : null);
            } else {
                baseUnit = byId.getOrDefault(baseUnit.getId(), baseUnit);
            }
            if (baseUnit == null) {
                continue;
            }
            collectStores(baseUnit, scope.isIncludeDescendants(), childrenByParent, stores);
        }
        return stores;
    }

    private void collectStores(OrganizationUnit root,
                               boolean includeDescendants,
                               Map<UUID, List<OrganizationUnit>> childrenByParent,
                               Set<String> stores) {
        ArrayDeque<OrganizationUnit> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            OrganizationUnit current = stack.pop();
            String storeId = normalizeStore(current.getStoreId());
            if (storeId != null) {
                stores.add(storeId);
            }
            if (!includeDescendants && !Objects.equals(current.getId(), root.getId())) {
                continue;
            }
            for (OrganizationUnit child : childrenByParent.getOrDefault(current.getId(), List.of())) {
                stack.push(child);
            }
        }
    }

    private List<UserOrganizationScopeDto> toScopeDtos(List<UserOrganizationScope> scopes) {
        return scopes.stream()
                .map(scope -> UserOrganizationScopeDto.builder()
                        .organizationUnitId(scope.getOrganizationUnit() != null ? scope.getOrganizationUnit().getId() : null)
                        .unitKey(scope.getOrganizationUnit() != null ? scope.getOrganizationUnit().getUnitKey() : null)
                        .unitName(scope.getOrganizationUnit() != null ? scope.getOrganizationUnit().getUnitName() : null)
                        .unitType(scope.getOrganizationUnit() != null ? scope.getOrganizationUnit().getUnitType() : null)
                        .includeDescendants(scope.isIncludeDescendants())
                        .build())
                .toList();
    }

    private List<String> normalizeStoreCollection(Collection<String> storeIds) {
        if (storeIds == null) {
            return List.of();
        }
        return storeIds.stream()
                .map(this::normalizeStore)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .toList();
    }

    private String normalizeStore(String storeId) {
        String trimmed = trimToNull(storeId);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultActor(String actor) {
        return trimToNull(actor) == null ? "system" : actor.trim();
    }

    public record StoreScopeFilter(boolean denyAll, List<String> storeIds) {
    }
}
