package com.recon.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.api.domain.AuditLedgerWriteRequest;
import com.recon.api.domain.Role;
import com.recon.api.domain.ScimGroupReference;
import com.recon.api.domain.ScimGroupResource;
import com.recon.api.domain.ScimListResponse;
import com.recon.api.domain.ScimMeta;
import com.recon.api.domain.ScimPatchOperation;
import com.recon.api.domain.ScimPatchRequest;
import com.recon.api.domain.TenantAuthConfigEntity;
import com.recon.api.domain.TenantScimGroup;
import com.recon.api.domain.User;
import com.recon.api.domain.UserScimGroupMembership;
import com.recon.api.repository.TenantAuthConfigRepository;
import com.recon.api.repository.TenantScimGroupRepository;
import com.recon.api.repository.UserRepository;
import com.recon.api.repository.UserScimGroupMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScimGroupPushService {

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 200;
    private static final Pattern DISPLAY_NAME_FILTER_PATTERN =
            Pattern.compile("(?i)^displayname\\s+eq\\s+\"([^\"]*)\"$");
    private static final Pattern MEMBER_SELECTOR_PATTERN =
            Pattern.compile("(?i)^members\\[value\\s+eq\\s+\"([^\"]+)\"\\]$");

    private final TenantAuthConfigRepository tenantAuthConfigRepository;
    private final TenantScimGroupRepository tenantScimGroupRepository;
    private final UserRepository userRepository;
    private final UserScimGroupMembershipRepository userScimGroupMembershipRepository;
    private final EnterpriseIdentityLifecycleService enterpriseIdentityLifecycleService;
    private final AuditLedgerService auditLedgerService;
    private final ObjectMapper objectMapper;

    @Transactional
    public User syncUserGroups(TenantAuthConfigEntity config,
                               User user,
                               Set<String> groups,
                               String actor) {
        Set<String> beforeGroups = loadUserGroupNames(config.getTenantId(), user.getId());
        Set<String> resolvedGroups = resolveRequestedGroupNames(config.getTenantId(), groups);
        ensureGroupsExist(config.getTenantId(), resolvedGroups, actor);
        userScimGroupMembershipRepository.deleteByTenantIdAndUser_Id(config.getTenantId(), user.getId());
        saveMemberships(config.getTenantId(), user, resolvedGroups, actor);

        User saved = reconcileUserAccess(config, user.getId(), actor, "SCIM_USER_GROUPS_SYNC");
        recordAudit(config.getTenantId(),
                "SCIM_USER",
                "SCIM_USER_GROUPS_SYNCED",
                "SCIM user groups synced",
                user.getId().toString(),
                actor,
                Map.of("groups", beforeGroups),
                Map.of("groups", resolvedGroups),
                Map.of("groupCount", resolvedGroups.size()));
        return saved;
    }

    @Transactional
    public User deprovisionUser(TenantAuthConfigEntity config,
                                User user,
                                String actor,
                                String reason,
                                boolean clearMemberships) {
        User before = cloneForAudit(user);
        if (clearMemberships) {
            userScimGroupMembershipRepository.deleteByTenantIdAndUser_Id(config.getTenantId(), user.getId());
        }

        String policy = normalizePolicy(config.getScimDeprovisionPolicy());
        if ("REMOVE_ACCESS".equals(policy)) {
            enterpriseIdentityLifecycleService.applyMappedRoles(
                    config.getTenantId(),
                    user,
                    Set.of(),
                    Set.of(),
                    "SCIM",
                    actor);
        }
        user.setActive(false);
        User saved = userRepository.save(user);
        recordAudit(saved.getTenantId(),
                "SCIM_USER",
                "SCIM_USER_DEPROVISIONED",
                "SCIM deprovision policy applied",
                saved.getId().toString(),
                actor,
                before,
                cloneForAudit(saved),
                Map.of(
                        "policy", policy,
                        "reason", defaultIfBlank(reason, "SCIM_EVENT"),
                        "clearMemberships", clearMemberships));
        return saved;
    }

    @Transactional(readOnly = true)
    public ScimListResponse<ScimGroupResource> listGroups(String tenantId,
                                                          String filter,
                                                          Integer startIndex,
                                                          Integer count) {
        resolveGroupPushConfig(tenantId);
        List<TenantScimGroup> groups = tenantScimGroupRepository.findByTenantIdOrderByDisplayNameAsc(tenantId);

        String normalizedFilter = trimToNull(filter);
        if (normalizedFilter != null) {
            Matcher matcher = DISPLAY_NAME_FILTER_PATTERN.matcher(normalizedFilter);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Unsupported SCIM group filter. Supported filter: displayName eq");
            }
            String expectedDisplayName = matcher.group(1);
            groups = groups.stream()
                    .filter(group -> equalsIgnoreCase(group.getDisplayName(), expectedDisplayName))
                    .toList();
        }

        Map<String, List<UserScimGroupMembership>> membershipsByGroup = aggregateMembershipsByGroupName(tenantId);
        int resolvedStartIndex = startIndex == null || startIndex < 1 ? 1 : startIndex;
        int resolvedCount = count == null || count < 1 ? DEFAULT_PAGE_SIZE : Math.min(count, MAX_PAGE_SIZE);
        int fromIndex = Math.min(groups.size(), resolvedStartIndex - 1);
        int toIndex = Math.min(groups.size(), fromIndex + resolvedCount);
        List<ScimGroupResource> page = groups.subList(fromIndex, toIndex).stream()
                .map(group -> toGroupResource(
                        tenantId,
                        group,
                        membershipsByGroup.getOrDefault(normalizeLower(group.getDisplayName()), List.of())))
                .toList();

        return ScimListResponse.<ScimGroupResource>builder()
                .totalResults(groups.size())
                .startIndex(resolvedStartIndex)
                .itemsPerPage(page.size())
                .resources(page)
                .build();
    }

    @Transactional(readOnly = true)
    public ScimGroupResource getGroup(String tenantId,
                                      String groupId) {
        resolveGroupPushConfig(tenantId);
        TenantScimGroup group = resolveGroup(tenantId, groupId);
        return toGroupResource(tenantId, group, loadGroupMemberships(tenantId, group.getDisplayName()));
    }

    @Transactional(readOnly = true)
    public List<ScimGroupReference> listUserGroups(String tenantId,
                                                   UUID userId) {
        List<UserScimGroupMembership> memberships =
                userScimGroupMembershipRepository.findByTenantIdAndUser_Id(tenantId, userId);
        if (memberships.isEmpty()) {
            return List.of();
        }

        Map<String, TenantScimGroup> groupsByName = tenantScimGroupRepository.findByTenantIdOrderByDisplayNameAsc(tenantId)
                .stream()
                .collect(Collectors.toMap(
                        group -> normalizeLower(group.getDisplayName()),
                        group -> group,
                        (left, right) -> left,
                        LinkedHashMap::new));

        return memberships.stream()
                .map(UserScimGroupMembership::getExternalGroup)
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .map(groupName -> {
                    TenantScimGroup group = groupsByName.get(normalizeLower(groupName));
                    return ScimGroupReference.builder()
                            .value(group != null ? group.getId().toString() : groupName)
                            .display(group != null ? group.getDisplayName() : groupName)
                            .ref(group != null ? "/api/scim/v2/" + tenantId + "/Groups/" + group.getId() : null)
                            .build();
                })
                .sorted((left, right) -> defaultIfBlank(left.getDisplay(), "")
                        .compareToIgnoreCase(defaultIfBlank(right.getDisplay(), "")))
                .toList();
    }

    @Transactional
    public ScimGroupResource createGroup(String tenantId,
                                         ScimGroupResource request,
                                         String actor) {
        TenantAuthConfigEntity config = resolveGroupPushConfig(tenantId);
        String displayName = requireField(request != null ? request.getDisplayName() : null, "displayName");
        tenantScimGroupRepository.findByTenantIdAndDisplayNameIgnoreCase(tenantId, displayName)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("SCIM group displayName already exists");
                });

        TenantScimGroup group = tenantScimGroupRepository.save(TenantScimGroup.builder()
                .tenantId(tenantId)
                .displayName(displayName)
                .updatedBy(defaultActor(actor))
                .build());

        Set<UUID> memberIds = resolveMemberUserIds(tenantId, request != null ? request.getMembers() : null);
        replaceMemberships(config, null, group.getDisplayName(), memberIds, actor);
        ScimGroupResource after = toGroupResource(tenantId, group, loadGroupMemberships(tenantId, group.getDisplayName()));
        recordAudit(tenantId,
                "SCIM_GROUP",
                "SCIM_GROUP_CREATED",
                "SCIM group created",
                group.getId().toString(),
                actor,
                null,
                after,
                Map.of("memberCount", memberIds.size()));
        return after;
    }

    @Transactional
    public ScimGroupResource replaceGroup(String tenantId,
                                          String groupId,
                                          ScimGroupResource request,
                                          String actor) {
        TenantAuthConfigEntity config = resolveGroupPushConfig(tenantId);
        TenantScimGroup group = resolveGroup(tenantId, groupId);
        String previousName = group.getDisplayName();
        ScimGroupResource before = toGroupResource(tenantId, group, loadGroupMemberships(tenantId, previousName));

        String targetName = firstNonBlank(
                request != null ? trimToNull(request.getDisplayName()) : null,
                previousName);
        validateRenamedGroup(tenantId, group.getId(), previousName, targetName);
        group.setDisplayName(targetName);
        group.setUpdatedBy(defaultActor(actor));
        TenantScimGroup savedGroup = tenantScimGroupRepository.save(group);

        Set<UUID> memberIds = resolveMemberUserIds(tenantId, request != null ? request.getMembers() : null);
        replaceMemberships(config, previousName, savedGroup.getDisplayName(), memberIds, actor);

        ScimGroupResource after = toGroupResource(
                tenantId,
                savedGroup,
                loadGroupMemberships(tenantId, savedGroup.getDisplayName()));
        recordAudit(tenantId,
                "SCIM_GROUP",
                "SCIM_GROUP_UPDATED",
                "SCIM group updated",
                savedGroup.getId().toString(),
                actor,
                before,
                after,
                Map.of("memberCount", memberIds.size()));
        return after;
    }

    @Transactional
    public ScimGroupResource patchGroup(String tenantId,
                                        String groupId,
                                        ScimPatchRequest request,
                                        String actor) {
        TenantAuthConfigEntity config = resolveGroupPushConfig(tenantId);
        TenantScimGroup group = resolveGroup(tenantId, groupId);
        String previousName = group.getDisplayName();
        List<UserScimGroupMembership> currentMemberships = loadGroupMemberships(tenantId, previousName);
        ScimGroupResource before = toGroupResource(tenantId, group, currentMemberships);

        MutableGroupState state = MutableGroupState.from(group, currentMemberships);
        List<ScimPatchOperation> operations = request != null && request.getOperations() != null
                ? request.getOperations()
                : List.of();
        if (operations.isEmpty()) {
            throw new IllegalArgumentException("SCIM group patch requires at least one operation");
        }

        for (ScimPatchOperation operation : operations) {
            String op = defaultIfBlank(operation != null ? operation.getOp() : null, "replace")
                    .toUpperCase(Locale.ROOT);
            String path = normalizeLower(operation != null ? operation.getPath() : null);
            if ("ADD".equals(op) || "REPLACE".equals(op)) {
                applyPatchUpsert(tenantId, state, path, operation != null ? operation.getValue() : null, "ADD".equals(op));
            } else if ("REMOVE".equals(op)) {
                applyPatchRemove(tenantId, state, path, operation != null ? operation.getValue() : null);
            } else {
                throw new IllegalArgumentException("Unsupported SCIM patch operation: " + op);
            }
        }

        String targetName = requireField(state.groupName, "displayName");
        validateRenamedGroup(tenantId, group.getId(), previousName, targetName);
        group.setDisplayName(targetName);
        group.setUpdatedBy(defaultActor(actor));
        TenantScimGroup savedGroup = tenantScimGroupRepository.save(group);

        replaceMemberships(config, previousName, savedGroup.getDisplayName(), state.memberIds, actor);
        ScimGroupResource after = toGroupResource(
                tenantId,
                savedGroup,
                loadGroupMemberships(tenantId, savedGroup.getDisplayName()));
        recordAudit(tenantId,
                "SCIM_GROUP",
                "SCIM_GROUP_UPDATED",
                "SCIM group updated",
                savedGroup.getId().toString(),
                actor,
                before,
                after,
                Map.of("memberCount", state.memberIds.size()));
        return after;
    }

    @Transactional
    public void deleteGroup(String tenantId,
                            String groupId,
                            String actor) {
        TenantAuthConfigEntity config = resolveGroupPushConfig(tenantId);
        TenantScimGroup group = resolveGroup(tenantId, groupId);
        List<UserScimGroupMembership> memberships = loadGroupMemberships(tenantId, group.getDisplayName());
        ScimGroupResource before = toGroupResource(tenantId, group, memberships);
        Set<UUID> affectedUserIds = memberships.stream()
                .map(membership -> membership.getUser().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        userScimGroupMembershipRepository.deleteByTenantIdAndExternalGroupIgnoreCase(tenantId, group.getDisplayName());
        tenantScimGroupRepository.delete(group);
        reconcileUsers(config, affectedUserIds, actor, "SCIM_GROUP_DELETED");

        recordAudit(tenantId,
                "SCIM_GROUP",
                "SCIM_GROUP_DELETED",
                "SCIM group deleted",
                group.getId().toString(),
                actor,
                before,
                null,
                Map.of("displayName", group.getDisplayName()));
    }

    private void replaceMemberships(TenantAuthConfigEntity config,
                                    String currentGroupName,
                                    String targetGroupName,
                                    Set<UUID> memberIds,
                                    String actor) {
        String tenantId = config.getTenantId();
        String resolvedTargetGroup = requireField(targetGroupName, "displayName");
        List<UserScimGroupMembership> currentMemberships = currentGroupName == null
                ? List.of()
                : loadGroupMemberships(tenantId, currentGroupName);

        Set<UUID> affectedUserIds = new LinkedHashSet<>(memberIds);
        currentMemberships.stream()
                .map(membership -> membership.getUser().getId())
                .forEach(affectedUserIds::add);

        if (currentGroupName != null) {
            userScimGroupMembershipRepository.deleteByTenantIdAndExternalGroupIgnoreCase(tenantId, currentGroupName);
        }

        for (UUID memberId : memberIds) {
            User user = userRepository.findByIdAndTenantId(memberId, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("SCIM group member user was not found"));
            userScimGroupMembershipRepository.save(UserScimGroupMembership.builder()
                    .tenantId(tenantId)
                    .user(user)
                    .externalGroup(resolvedTargetGroup)
                    .updatedBy(defaultActor(actor))
                    .build());
        }

        reconcileUsers(config, affectedUserIds, actor, "SCIM_GROUP_MEMBERSHIP_CHANGED");
    }

    private void reconcileUsers(TenantAuthConfigEntity config,
                                Set<UUID> userIds,
                                String actor,
                                String reason) {
        for (UUID userId : userIds) {
            if (userId == null) {
                continue;
            }
            userRepository.findByIdAndTenantId(userId, config.getTenantId())
                    .ifPresent(user -> reconcileUserAccess(config, user.getId(), actor, reason));
        }
    }

    private User reconcileUserAccess(TenantAuthConfigEntity config,
                                     UUID userId,
                                     String actor,
                                     String reason) {
        User user = userRepository.findByIdAndTenantId(userId, config.getTenantId())
                .orElseThrow(() -> new NoSuchElementException("SCIM user was not found"));
        Set<String> groups = loadUserGroupNames(config.getTenantId(), userId);
        Set<Role> mappedRoles = enterpriseIdentityLifecycleService.resolveMappedRoles(
                config.getTenantId(),
                groups,
                "SCIM",
                false);
        if (groups.isEmpty() || mappedRoles.isEmpty()) {
            return deprovisionUser(config, user, actor, reason, false);
        }

        boolean reactivated = !user.isActive();
        enterpriseIdentityLifecycleService.applyMappedRoles(
                config.getTenantId(),
                user,
                mappedRoles,
                groups,
                "SCIM",
                actor);
        user.setActive(true);
        User saved = userRepository.save(user);
        if (reactivated) {
            recordAudit(saved.getTenantId(),
                    "SCIM_USER",
                    "SCIM_USER_REACTIVATED",
                    "SCIM user reactivated",
                    saved.getId().toString(),
                    actor,
                    null,
                    cloneForAudit(saved),
                    Map.of("groups", groups));
        }
        return saved;
    }

    private Set<String> resolveRequestedGroupNames(String tenantId,
                                                   Set<String> groups) {
        LinkedHashMap<String, String> resolved = new LinkedHashMap<>();
        for (String value : groups == null ? Set.<String>of() : groups) {
            String token = trimToNull(value);
            if (token == null) {
                continue;
            }

            TenantScimGroup existing = resolveGroupToken(tenantId, token);
            String groupName = existing != null ? existing.getDisplayName() : token;
            resolved.putIfAbsent(normalizeLower(groupName), groupName);
        }
        return new LinkedHashSet<>(resolved.values());
    }

    private TenantScimGroup resolveGroupToken(String tenantId,
                                              String token) {
        UUID id = parseUuidOrNull(token);
        if (id != null) {
            return tenantScimGroupRepository.findByIdAndTenantId(id, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("SCIM group id was not found"));
        }
        return tenantScimGroupRepository.findByTenantIdAndDisplayNameIgnoreCase(tenantId, token).orElse(null);
    }

    private void ensureGroupsExist(String tenantId,
                                   Set<String> groupNames,
                                   String actor) {
        for (String groupName : groupNames) {
            if (tenantScimGroupRepository.findByTenantIdAndDisplayNameIgnoreCase(tenantId, groupName).isEmpty()) {
                tenantScimGroupRepository.save(TenantScimGroup.builder()
                        .tenantId(tenantId)
                        .displayName(groupName)
                        .updatedBy(defaultActor(actor))
                        .build());
            }
        }
    }

    private void saveMemberships(String tenantId,
                                 User user,
                                 Collection<String> groupNames,
                                 String actor) {
        for (String groupName : groupNames) {
            String resolvedGroupName = trimToNull(groupName);
            if (resolvedGroupName == null) {
                continue;
            }
            userScimGroupMembershipRepository.save(UserScimGroupMembership.builder()
                    .tenantId(tenantId)
                    .user(user)
                    .externalGroup(resolvedGroupName)
                    .updatedBy(defaultActor(actor))
                    .build());
        }
    }

    private TenantAuthConfigEntity resolveGroupPushConfig(String tenantId) {
        TenantAuthConfigEntity config = tenantAuthConfigRepository.findById(requireField(tenantId, "Tenant"))
                .orElseThrow(() -> new IllegalArgumentException("SCIM tenant configuration was not found"));
        if (!config.isScimEnabled()) {
            throw new IllegalArgumentException("SCIM provisioning is not enabled for this tenant");
        }
        if (!config.isScimGroupPushEnabled()) {
            throw new IllegalArgumentException("SCIM group push is not enabled for this tenant");
        }
        return config;
    }

    private TenantScimGroup resolveGroup(String tenantId,
                                         String groupId) {
        UUID id = parseUuidOrNull(groupId);
        if (id == null) {
            throw new NoSuchElementException("SCIM group was not found");
        }
        return tenantScimGroupRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("SCIM group was not found"));
    }

    private void validateRenamedGroup(String tenantId,
                                      UUID currentGroupId,
                                      String currentName,
                                      String targetName) {
        String resolvedTarget = requireField(targetName, "displayName");
        if (equalsIgnoreCase(currentName, resolvedTarget)) {
            return;
        }
        tenantScimGroupRepository.findByTenantIdAndDisplayNameIgnoreCase(tenantId, resolvedTarget)
                .filter(existing -> !Objects.equals(existing.getId(), currentGroupId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("SCIM group displayName already exists");
                });
    }

    private Set<UUID> resolveMemberUserIds(String tenantId,
                                           List<ScimGroupReference> members) {
        LinkedHashSet<UUID> resolved = new LinkedHashSet<>();
        if (members == null) {
            return resolved;
        }
        for (ScimGroupReference member : members) {
            String token = firstNonBlank(
                    member != null ? trimToNull(member.getValue()) : null,
                    extractResourceId(member != null ? member.getRef() : null));
            UUID userId = parseUuidOrNull(token);
            if (userId == null) {
                throw new IllegalArgumentException("SCIM group members must reference an application user id");
            }
            if (userRepository.findByIdAndTenantId(userId, tenantId).isEmpty()) {
                throw new IllegalArgumentException("SCIM group member user was not found");
            }
            resolved.add(userId);
        }
        return resolved;
    }

    private String extractResourceId(String ref) {
        String normalized = trimToNull(ref);
        if (normalized == null) {
            return null;
        }
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == normalized.length() - 1) {
            return null;
        }
        return normalized.substring(lastSlash + 1);
    }

    private ScimGroupResource toGroupResource(String tenantId,
                                              TenantScimGroup group,
                                              List<UserScimGroupMembership> memberships) {
        LocalDateTime lastModified = group.getUpdatedAt() != null ? group.getUpdatedAt() : group.getCreatedAt();
        if (lastModified == null) {
            lastModified = LocalDateTime.now();
        }
        for (UserScimGroupMembership membership : memberships) {
            if (membership.getUpdatedAt() != null && membership.getUpdatedAt().isAfter(lastModified)) {
                lastModified = membership.getUpdatedAt();
            }
        }

        List<ScimGroupReference> members = memberships.stream()
                .map(UserScimGroupMembership::getUser)
                .filter(Objects::nonNull)
                .sorted((left, right) -> defaultIfBlank(firstNonBlank(left.getFullName(), left.getUsername()), "")
                        .compareToIgnoreCase(defaultIfBlank(firstNonBlank(right.getFullName(), right.getUsername()), "")))
                .map(user -> ScimGroupReference.builder()
                        .value(user.getId().toString())
                        .display(firstNonBlank(user.getFullName(), user.getUsername()))
                        .ref("/api/scim/v2/" + tenantId + "/Users/" + user.getId())
                        .build())
                .toList();

        return ScimGroupResource.builder()
                .id(group.getId().toString())
                .displayName(group.getDisplayName())
                .members(members)
                .meta(ScimMeta.builder()
                        .resourceType("Group")
                        .created(toIso(group.getCreatedAt()))
                        .lastModified(toIso(lastModified))
                        .location("/api/scim/v2/" + tenantId + "/Groups/" + group.getId())
                        .build())
                .build();
    }

    private List<UserScimGroupMembership> loadGroupMemberships(String tenantId,
                                                               String groupName) {
        return userScimGroupMembershipRepository.findByTenantIdAndExternalGroupIgnoreCase(
                tenantId,
                requireField(groupName, "displayName"));
    }

    private Map<String, List<UserScimGroupMembership>> aggregateMembershipsByGroupName(String tenantId) {
        return userScimGroupMembershipRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.groupingBy(
                        membership -> normalizeLower(membership.getExternalGroup()),
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    private Set<String> loadUserGroupNames(String tenantId,
                                           UUID userId) {
        LinkedHashMap<String, String> resolved = new LinkedHashMap<>();
        for (UserScimGroupMembership membership : userScimGroupMembershipRepository.findByTenantIdAndUser_Id(tenantId, userId)) {
            String groupName = trimToNull(membership.getExternalGroup());
            if (groupName != null) {
                resolved.putIfAbsent(normalizeLower(groupName), groupName);
            }
        }
        return new LinkedHashSet<>(resolved.values());
    }

    private void applyPatchUpsert(String tenantId,
                                  MutableGroupState state,
                                  String path,
                                  Object value,
                                  boolean additive) {
        if (path == null) {
            ScimGroupResource resource = objectMapper.convertValue(value, ScimGroupResource.class);
            mergeGroupResource(tenantId, state, resource, additive);
            return;
        }
        switch (path) {
            case "displayname" -> state.groupName = requireField(asString(value), "displayName");
            case "members" -> {
                Set<UUID> memberIds = resolveMemberUserIds(tenantId, asMemberList(value));
                if (additive) {
                    state.memberIds.addAll(memberIds);
                } else {
                    state.memberIds.clear();
                    state.memberIds.addAll(memberIds);
                }
            }
            default -> throw new IllegalArgumentException("Unsupported SCIM group patch path: " + path);
        }
    }

    private void applyPatchRemove(String tenantId,
                                  MutableGroupState state,
                                  String path,
                                  Object value) {
        if (path == null) {
            throw new IllegalArgumentException("SCIM remove patch requires a path");
        }

        Matcher selectorMatcher = MEMBER_SELECTOR_PATTERN.matcher(path);
        if (selectorMatcher.matches()) {
            UUID memberId = parseUuidOrNull(selectorMatcher.group(1));
            if (memberId == null) {
                throw new IllegalArgumentException("SCIM group members must reference an application user id");
            }
            state.memberIds.remove(memberId);
            return;
        }

        switch (path) {
            case "members" -> {
                if (value == null) {
                    state.memberIds.clear();
                    return;
                }
                state.memberIds.removeAll(resolveMemberUserIds(tenantId, asMemberList(value)));
            }
            case "displayname" -> throw new IllegalArgumentException("SCIM group displayName cannot be removed");
            default -> throw new IllegalArgumentException("Unsupported SCIM group patch path: " + path);
        }
    }

    private void mergeGroupResource(String tenantId,
                                    MutableGroupState state,
                                    ScimGroupResource resource,
                                    boolean additive) {
        if (resource == null) {
            return;
        }
        if (resource.getDisplayName() != null) {
            state.groupName = requireField(resource.getDisplayName(), "displayName");
        }
        if (resource.getMembers() != null) {
            Set<UUID> memberIds = resolveMemberUserIds(tenantId, resource.getMembers());
            if (additive) {
                state.memberIds.addAll(memberIds);
            } else {
                state.memberIds.clear();
                state.memberIds.addAll(memberIds);
            }
        }
    }

    private List<ScimGroupReference> asMemberList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return List.of();
            }
            return objectMapper.convertValue(value, new TypeReference<List<ScimGroupReference>>() {
            });
        }
        return List.of(objectMapper.convertValue(value, ScimGroupReference.class));
    }

    private User cloneForAudit(User user) {
        return User.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .tenantId(user.getTenantId())
                .active(user.isActive())
                .identityProvider(user.getIdentityProvider())
                .externalSubject(user.getExternalSubject())
                .directoryExternalId(user.getDirectoryExternalId())
                .emailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .roles(user.getRoles() == null ? Set.of() : new LinkedHashSet<>(user.getRoles()))
                .storeIds(user.getStoreIds() == null ? Set.of() : new LinkedHashSet<>(user.getStoreIds()))
                .build();
    }

    private void recordAudit(String tenantId,
                             String entityType,
                             String actionType,
                             String title,
                             String entityKey,
                             String actor,
                             Object before,
                             Object after,
                             Map<String, Object> metadata) {
        auditLedgerService.record(AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("SECURITY")
                .moduleKey("SECURITY")
                .entityType(entityType)
                .entityKey(entityKey)
                .actionType(actionType)
                .title(title)
                .actor(defaultActor(actor))
                .status(actionType)
                .referenceKey(entityKey)
                .controlFamily("ACCESS_CONTROL")
                .evidenceTags(List.of("SECURITY", "SCIM", "PROVISIONING"))
                .beforeState(before)
                .afterState(after)
                .metadata(metadata == null || metadata.isEmpty() ? null : new LinkedHashMap<>(metadata))
                .build());
    }

    private String toIso(LocalDateTime value) {
        return value == null
                ? null
                : value.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private String normalizePolicy(String value) {
        return defaultIfBlank(value, "DEACTIVATE").toUpperCase(Locale.ROOT);
    }

    private String normalizeLower(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String stringValue) {
            return trimToNull(stringValue);
        }
        return trimToNull(Objects.toString(value, null));
    }

    private UUID parseUuidOrNull(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean equalsIgnoreCase(String left,
                                     String right) {
        return trimToNull(left) != null
                && trimToNull(right) != null
                && left.equalsIgnoreCase(right);
    }

    private String requireField(String value,
                                String label) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return trimmed;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultIfBlank(String value,
                                  String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String defaultActor(String actor) {
        return defaultIfBlank(actor, "scim");
    }

    private static final class MutableGroupState {
        private String groupName;
        private Set<UUID> memberIds = new LinkedHashSet<>();

        private static MutableGroupState from(TenantScimGroup group,
                                              List<UserScimGroupMembership> memberships) {
            MutableGroupState state = new MutableGroupState();
            state.groupName = group.getDisplayName();
            state.memberIds = memberships.stream()
                    .map(UserScimGroupMembership::getUser)
                    .filter(Objects::nonNull)
                    .map(User::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return state;
        }
    }
}
