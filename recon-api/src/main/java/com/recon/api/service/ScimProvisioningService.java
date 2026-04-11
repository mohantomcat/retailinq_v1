package com.recon.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.api.domain.AuditLedgerWriteRequest;
import com.recon.api.domain.ScimEmail;
import com.recon.api.domain.ScimGroupReference;
import com.recon.api.domain.ScimListResponse;
import com.recon.api.domain.ScimMeta;
import com.recon.api.domain.ScimName;
import com.recon.api.domain.ScimPatchOperation;
import com.recon.api.domain.ScimPatchRequest;
import com.recon.api.domain.ScimUserRequest;
import com.recon.api.domain.ScimUserResource;
import com.recon.api.domain.TenantAuthConfigEntity;
import com.recon.api.domain.User;
import com.recon.api.repository.TenantAuthConfigRepository;
import com.recon.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

@Service
@RequiredArgsConstructor
public class ScimProvisioningService {

    private static final String SCHEMA_LIST_RESPONSE = "urn:ietf:params:scim:api:messages:2.0:ListResponse";
    private static final String SCHEMA_PATCH_OP = "urn:ietf:params:scim:api:messages:2.0:PatchOp";
    private static final String SCHEMA_RESOURCE_TYPE = "urn:ietf:params:scim:schemas:core:2.0:ResourceType";
    private static final String SCHEMA_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Schema";
    private static final String SCHEMA_SERVICE_PROVIDER_CONFIG = "urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig";
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 200;
    private static final Pattern STRING_FILTER_PATTERN = Pattern.compile("(?i)^([a-zA-Z.]+)\\s+eq\\s+\"([^\"]*)\"$");
    private static final Pattern BOOLEAN_FILTER_PATTERN = Pattern.compile("(?i)^(active)\\s+eq\\s+(true|false)$");

    private final TenantAuthConfigRepository tenantAuthConfigRepository;
    private final UserRepository userRepository;
    private final EnterpriseIdentityLifecycleService enterpriseIdentityLifecycleService;
    private final AuditLedgerService auditLedgerService;
    private final ObjectMapper objectMapper;

    public Map<String, Object> getServiceProviderConfig(String tenantId) {
        resolveConfig(tenantId);
        return Map.of(
                "schemas", List.of(SCHEMA_SERVICE_PROVIDER_CONFIG),
                "patch", Map.of("supported", true),
                "bulk", Map.of("supported", false, "maxOperations", 0, "maxPayloadSize", 0),
                "filter", Map.of("supported", true, "maxResults", MAX_PAGE_SIZE),
                "changePassword", Map.of("supported", false),
                "sort", Map.of("supported", false),
                "etag", Map.of("supported", false),
                "authenticationSchemes", List.of(Map.of(
                        "type", "oauthbearertoken",
                        "name", "Bearer Token",
                        "description", "Tenant-scoped bearer token for SCIM provisioning",
                        "primary", true)));
    }

    public Map<String, Object> getSchemas(String tenantId) {
        resolveConfig(tenantId);
        List<Map<String, Object>> resources = new ArrayList<>();
        resources.add(Map.of(
                "schemas", List.of(SCHEMA_SCHEMA),
                "id", "urn:ietf:params:scim:schemas:core:2.0:User",
                "name", "User",
                "description", "User Account",
                "attributes", List.of(
                        attribute("userName", "string", true, false, "readWrite"),
                        attribute("externalId", "string", false, false, "readWrite"),
                        attribute("displayName", "string", false, false, "readWrite"),
                        attribute("active", "boolean", false, false, "readWrite"),
                        attribute("emails", "complex", false, true, "readWrite"),
                        attribute("name", "complex", false, false, "readWrite"))));
        resources.add(Map.of(
                "schemas", List.of(SCHEMA_SCHEMA),
                "id", SCHEMA_PATCH_OP,
                "name", "PatchOp",
                "description", "SCIM patch operations"));
        return Map.of(
                "schemas", List.of(SCHEMA_LIST_RESPONSE),
                "totalResults", resources.size(),
                "startIndex", 1,
                "itemsPerPage", resources.size(),
                "Resources", resources);
    }

    public Map<String, Object> getResourceTypes(String tenantId) {
        resolveConfig(tenantId);
        Map<String, Object> userResourceType = Map.of(
                "schemas", List.of(SCHEMA_RESOURCE_TYPE),
                "id", "User",
                "name", "User",
                "endpoint", "/Users",
                "schema", "urn:ietf:params:scim:schemas:core:2.0:User");
        return Map.of(
                "schemas", List.of(SCHEMA_LIST_RESPONSE),
                "totalResults", 1,
                "startIndex", 1,
                "itemsPerPage", 1,
                "Resources", List.of(userResourceType));
    }

    public ScimListResponse<ScimUserResource> listUsers(String tenantId,
                                                        String filter,
                                                        Integer startIndex,
                                                        Integer count) {
        resolveConfig(tenantId);
        ScimFilterSpec filterSpec = parseFilter(filter);
        List<User> users = userRepository.findByTenantId(tenantId).stream()
                .filter(user -> matchesFilter(user, filterSpec))
                .sorted((left, right) -> {
                    LocalDateTime leftValue = left.getCreatedAt() != null ? left.getCreatedAt() : LocalDateTime.MIN;
                    LocalDateTime rightValue = right.getCreatedAt() != null ? right.getCreatedAt() : LocalDateTime.MIN;
                    return leftValue.compareTo(rightValue);
                })
                .toList();

        int resolvedStartIndex = startIndex == null || startIndex < 1 ? 1 : startIndex;
        int resolvedCount = count == null || count < 1 ? DEFAULT_PAGE_SIZE : Math.min(count, MAX_PAGE_SIZE);
        int fromIndex = Math.min(users.size(), resolvedStartIndex - 1);
        int toIndex = Math.min(users.size(), fromIndex + resolvedCount);
        List<ScimUserResource> resources = users.subList(fromIndex, toIndex).stream()
                .map(user -> toResource(tenantId, user))
                .toList();

        return ScimListResponse.<ScimUserResource>builder()
                .totalResults(users.size())
                .startIndex(resolvedStartIndex)
                .itemsPerPage(resources.size())
                .resources(resources)
                .build();
    }

    public ScimUserResource getUser(String tenantId,
                                    String userId) {
        resolveConfig(tenantId);
        return toResource(tenantId, resolveUser(tenantId, userId));
    }

    @Transactional
    public ScimUserResource createUser(String tenantId,
                                       ScimUserRequest request,
                                       String actor) {
        TenantAuthConfigEntity config = resolveConfig(tenantId);
        ResolvedScimUserInput input = resolveCreateInput(request);
        User user = enterpriseIdentityLifecycleService.syncScimIdentity(
                config,
                new EnterpriseIdentityLifecycleService.ScimIdentityProfile(
                        input.externalId(),
                        input.userName(),
                        input.email(),
                        input.fullName(),
                        input.active(),
                        input.groupsProvided() ? input.groups() : null),
                defaultActor(actor));
        return toResource(tenantId, user);
    }

    @Transactional
    public ScimUserResource replaceUser(String tenantId,
                                        String userId,
                                        ScimUserRequest request,
                                        String actor) {
        TenantAuthConfigEntity config = resolveConfig(tenantId);
        User user = resolveUser(tenantId, userId);
        User before = cloneForAudit(user);
        ResolvedScimUserInput input = resolveReplaceInput(user, request);
        User saved = applyUserUpdate(config, user, input, defaultActor(actor));
        recordAudit(saved.getTenantId(),
                "SCIM_USER_REPLACED",
                "SCIM user replaced",
                saved.getId().toString(),
                actor,
                before,
                cloneForAudit(saved));
        return toResource(tenantId, saved);
    }

    private User applyUserUpdate(TenantAuthConfigEntity config,
                                 User user,
                                 ResolvedScimUserInput input,
                                 String actor) {
        String tenantId = config.getTenantId();
        String userName = requireField(input.userName(), "userName");
        String email = requireField(input.email(), "Email");
        validateAllowedEmailDomain(config, email);

        if (!userName.equalsIgnoreCase(user.getUsername())
                && userRepository.existsByTenantIdAndUsernameIgnoreCase(tenantId, userName)) {
            throw new IllegalArgumentException("SCIM userName is already assigned to another user");
        }
        if (!email.equalsIgnoreCase(user.getEmail())
                && userRepository.existsByTenantIdAndEmailIgnoreCase(tenantId, email)) {
            throw new IllegalArgumentException("SCIM email is already assigned to another user");
        }

        String externalId = trimToNull(input.externalId());
        if (externalId != null) {
            userRepository.findByTenantIdAndDirectoryExternalIdIgnoreCase(tenantId, externalId)
                    .filter(existing -> !Objects.equals(existing.getId(), user.getId()))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("SCIM externalId is already assigned to another user");
                    });
        }

        user.setUsername(userName);
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setFullName(input.fullName());
        user.setActive(input.active());
        user.setDirectoryExternalId(externalId);
        if (trimToNull(user.getIdentityProvider()) == null
                || "LOCAL".equalsIgnoreCase(user.getIdentityProvider())
                || "SCIM".equalsIgnoreCase(user.getIdentityProvider())) {
            user.setIdentityProvider("SCIM");
        }
        if (externalId != null) {
            user.setExternalSubject(externalId);
        } else if ("SCIM".equalsIgnoreCase(user.getIdentityProvider())) {
            user.setExternalSubject(defaultIfBlank(user.getExternalSubject(), userName));
        }

        if (input.groupsProvided()) {
            enterpriseIdentityLifecycleService.syncMappedRoles(
                    tenantId,
                    user,
                    input.groups(),
                    "SCIM",
                    actor,
                    true,
                    false);
        }

        return userRepository.save(user);
    }

    private User resolveUser(String tenantId,
                             String userId) {
        UUID id;
        try {
            id = UUID.fromString(requireField(userId, "SCIM user id"));
        } catch (IllegalArgumentException ex) {
            throw new NoSuchElementException("SCIM user was not found");
        }
        return userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("SCIM user was not found"));
    }

    private TenantAuthConfigEntity resolveConfig(String tenantId) {
        TenantAuthConfigEntity config = tenantAuthConfigRepository.findById(requireField(tenantId, "Tenant"))
                .orElseThrow(() -> new IllegalArgumentException("SCIM tenant configuration was not found"));
        if (!config.isScimEnabled()) {
            throw new IllegalArgumentException("SCIM provisioning is not enabled for this tenant");
        }
        return config;
    }

    private ResolvedScimUserInput resolveCreateInput(ScimUserRequest request) {
        String userName = requireField(request != null ? request.getUserName() : null, "userName");
        String email = resolveEmail(request != null ? request.getEmails() : null);
        ScimName name = request != null ? request.getName() : null;
        String displayName = firstNonBlank(
                trimToNull(request != null ? request.getDisplayName() : null),
                trimToNull(name != null ? name.getFormatted() : null),
                buildName(name != null ? name.getGivenName() : null, name != null ? name.getFamilyName() : null),
                userName);
        Set<String> groups = normalizeGroups(request != null ? request.getGroups() : null);
        boolean groupsProvided = request != null && request.getGroups() != null;
        return new ResolvedScimUserInput(
                trimToNull(request != null ? request.getExternalId() : null),
                userName,
                email,
                displayName,
                name != null ? trimToNull(name.getGivenName()) : null,
                name != null ? trimToNull(name.getFamilyName()) : null,
                request == null || request.getActive() == null || request.getActive(),
                groupsProvided,
                groups);
    }

    private ResolvedScimUserInput resolveReplaceInput(User user,
                                                      ScimUserRequest request) {
        ScimName name = request != null ? request.getName() : null;
        return new ResolvedScimUserInput(
                request != null && request.getExternalId() != null
                        ? trimToNull(request.getExternalId())
                        : trimToNull(user.getDirectoryExternalId()),
                firstNonBlank(trimToNull(request != null ? request.getUserName() : null), user.getUsername()),
                firstNonBlank(resolveEmail(request != null ? request.getEmails() : null), user.getEmail()),
                firstNonBlank(
                        trimToNull(request != null ? request.getDisplayName() : null),
                        trimToNull(name != null ? name.getFormatted() : null),
                        user.getFullName(),
                        user.getUsername()),
                name != null && name.getGivenName() != null ? trimToNull(name.getGivenName()) : null,
                name != null && name.getFamilyName() != null ? trimToNull(name.getFamilyName()) : null,
                request != null && request.getActive() != null ? request.getActive() : user.isActive(),
                request != null && request.getGroups() != null,
                normalizeGroups(request != null ? request.getGroups() : null));
    }

    @Transactional
    public ScimUserResource patchUser(String tenantId,
                                      String userId,
                                      ScimPatchRequest request,
                                      String actor) {
        TenantAuthConfigEntity config = resolveConfig(tenantId);
        User user = resolveUser(tenantId, userId);
        User before = cloneForAudit(user);
        ResolvedScimUserInput input = resolvePatchedInput(user, request);
        User saved = applyUserUpdate(config, user, input, defaultActor(actor));
        recordAudit(saved.getTenantId(),
                "SCIM_USER_PATCHED",
                "SCIM user patched",
                saved.getId().toString(),
                actor,
                before,
                cloneForAudit(saved));
        return toResource(tenantId, saved);
    }

    @Transactional
    public void deactivateUser(String tenantId,
                               String userId,
                               String actor) {
        resolveConfig(tenantId);
        User user = resolveUser(tenantId, userId);
        User before = cloneForAudit(user);
        user.setActive(false);
        User saved = userRepository.save(user);
        recordAudit(saved.getTenantId(),
                "SCIM_USER_DEACTIVATED",
                "SCIM user deactivated",
                saved.getId().toString(),
                actor,
                before,
                cloneForAudit(saved));
    }

    private ResolvedScimUserInput resolvePatchedInput(User user,
                                                      ScimPatchRequest request) {
        MutableScimUserInput input = MutableScimUserInput.fromUser(user);
        List<ScimPatchOperation> operations = request != null && request.getOperations() != null
                ? request.getOperations()
                : List.of();
        if (operations.isEmpty()) {
            throw new IllegalArgumentException("SCIM patch requires at least one operation");
        }
        for (ScimPatchOperation operation : operations) {
            String op = defaultIfBlank(operation != null ? operation.getOp() : null, "replace")
                    .toUpperCase(Locale.ROOT);
            String path = normalizePatchPath(operation != null ? operation.getPath() : null);
            if ("ADD".equals(op) || "REPLACE".equals(op)) {
                applyPatchUpsert(input, path, operation != null ? operation.getValue() : null);
            } else if ("REMOVE".equals(op)) {
                applyPatchRemove(input, path);
            } else {
                throw new IllegalArgumentException("Unsupported SCIM patch operation: " + op);
            }
        }
        return input.toRecord();
    }

    private void applyPatchUpsert(MutableScimUserInput input,
                                  String path,
                                  Object value) {
        if (path == null) {
            ScimUserRequest request = objectMapper.convertValue(value, ScimUserRequest.class);
            mergeRequestIntoInput(input, request);
            return;
        }
        switch (path) {
            case "username" -> input.userName = requireField(asString(value), "Patch userName");
            case "externalid" -> input.externalId = trimToNull(asString(value));
            case "displayname" -> input.displayName = trimToNull(asString(value));
            case "active" -> input.active = asBoolean(value, "Patch active");
            case "name.formatted" -> input.displayName = trimToNull(asString(value));
            case "name.givenname" -> input.givenName = trimToNull(asString(value));
            case "name.familyname" -> input.familyName = trimToNull(asString(value));
            case "emails" -> input.email = resolveEmail(asEmailList(value));
            case "emails.value" -> input.email = requireField(asString(value), "Patch emails.value");
            case "groups" -> {
                input.groupsProvided = true;
                input.groups = normalizeGroups(asGroupList(value));
            }
            default -> throw new IllegalArgumentException("Unsupported SCIM patch path: " + path);
        }
    }

    private void applyPatchRemove(MutableScimUserInput input,
                                  String path) {
        if (path == null) {
            throw new IllegalArgumentException("SCIM remove patch requires a path");
        }
        if (path.startsWith("groups[")) {
            throw new IllegalArgumentException("SCIM group selector patches are not supported; replace groups as a full list");
        }
        switch (path) {
            case "displayname", "name.formatted" -> input.displayName = null;
            case "name.givenname" -> input.givenName = null;
            case "name.familyname" -> input.familyName = null;
            case "externalid" -> input.externalId = null;
            case "groups" -> {
                input.groupsProvided = true;
                input.groups = new LinkedHashSet<>();
            }
            case "emails", "emails.value" ->
                    throw new IllegalArgumentException("SCIM email cannot be removed because the application requires it");
            case "username" ->
                    throw new IllegalArgumentException("SCIM userName cannot be removed");
            case "active" ->
                    throw new IllegalArgumentException("Use replace active=false instead of removing active");
            default -> throw new IllegalArgumentException("Unsupported SCIM patch path: " + path);
        }
    }

    private void mergeRequestIntoInput(MutableScimUserInput input,
                                       ScimUserRequest request) {
        if (request == null) {
            return;
        }
        if (request.getUserName() != null) {
            input.userName = requireField(request.getUserName(), "userName");
        }
        if (request.getExternalId() != null) {
            input.externalId = trimToNull(request.getExternalId());
        }
        if (request.getDisplayName() != null) {
            input.displayName = trimToNull(request.getDisplayName());
        }
        if (request.getActive() != null) {
            input.active = request.getActive();
        }
        if (request.getEmails() != null) {
            input.email = resolveEmail(request.getEmails());
        }
        if (request.getName() != null) {
            if (request.getName().getFormatted() != null) {
                input.displayName = trimToNull(request.getName().getFormatted());
            }
            if (request.getName().getGivenName() != null) {
                input.givenName = trimToNull(request.getName().getGivenName());
            }
            if (request.getName().getFamilyName() != null) {
                input.familyName = trimToNull(request.getName().getFamilyName());
            }
        }
        if (request.getGroups() != null) {
            input.groupsProvided = true;
            input.groups = normalizeGroups(request.getGroups());
        }
    }

    private List<ScimEmail> asEmailList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return List.of();
        }
        return objectMapper.convertValue(value, new TypeReference<List<ScimEmail>>() {
        });
    }

    private List<ScimGroupReference> asGroupList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return List.of();
        }
        return objectMapper.convertValue(value, new TypeReference<List<ScimGroupReference>>() {
        });
    }

    private boolean matchesFilter(User user,
                                  ScimFilterSpec filterSpec) {
        if (filterSpec == null) {
            return true;
        }
        return switch (filterSpec.attribute()) {
            case "username" -> equalsIgnoreCase(user.getUsername(), filterSpec.value());
            case "externalid" -> equalsIgnoreCase(user.getDirectoryExternalId(), filterSpec.value());
            case "emails.value" -> equalsIgnoreCase(user.getEmail(), filterSpec.value());
            case "active" -> user.isActive() == Boolean.parseBoolean(filterSpec.value());
            default -> true;
        };
    }

    private ScimFilterSpec parseFilter(String filter) {
        String normalized = trimToNull(filter);
        if (normalized == null) {
            return null;
        }
        Matcher booleanMatcher = BOOLEAN_FILTER_PATTERN.matcher(normalized);
        if (booleanMatcher.matches()) {
            return new ScimFilterSpec("active", booleanMatcher.group(2).toLowerCase(Locale.ROOT));
        }
        Matcher stringMatcher = STRING_FILTER_PATTERN.matcher(normalized);
        if (stringMatcher.matches()) {
            String attribute = normalizePatchPath(stringMatcher.group(1));
            if (!Set.of("username", "externalid", "emails.value").contains(attribute)) {
                throw new IllegalArgumentException("Unsupported SCIM filter. Supported filters: userName eq, externalId eq, emails.value eq, active eq");
            }
            return new ScimFilterSpec(attribute, stringMatcher.group(2));
        }
        throw new IllegalArgumentException("Unsupported SCIM filter. Supported filters: userName eq, externalId eq, emails.value eq, active eq");
    }

    private Set<String> normalizeGroups(List<ScimGroupReference> groups) {
        if (groups == null) {
            return new LinkedHashSet<>();
        }
        return groups.stream()
                .map(group -> firstNonBlank(
                        group != null ? trimToNull(group.getDisplay()) : null,
                        group != null ? trimToNull(group.getValue()) : null))
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String resolveEmail(List<ScimEmail> emails) {
        if (emails == null || emails.isEmpty()) {
            return null;
        }
        return emails.stream()
                .filter(item -> Boolean.TRUE.equals(item.getPrimary()))
                .map(ScimEmail::getValue)
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> emails.stream()
                        .map(ScimEmail::getValue)
                        .map(this::trimToNull)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null));
    }

    private ScimUserResource toResource(String tenantId,
                                        User user) {
        String fullName = firstNonBlank(user.getFullName(), user.getUsername());
        return ScimUserResource.builder()
                .id(user.getId().toString())
                .externalId(user.getDirectoryExternalId())
                .userName(user.getUsername())
                .displayName(fullName)
                .active(user.isActive())
                .name(ScimName.builder()
                        .formatted(fullName)
                        .build())
                .emails(user.getEmail() == null
                        ? List.of()
                        : List.of(ScimEmail.builder()
                        .value(user.getEmail())
                        .type("work")
                        .primary(true)
                        .build()))
                .meta(ScimMeta.builder()
                        .resourceType("User")
                        .created(toIso(user.getCreatedAt()))
                        .lastModified(toIso(user.getUpdatedAt() != null ? user.getUpdatedAt() : user.getCreatedAt()))
                        .location("/api/scim/v2/" + tenantId + "/Users/" + user.getId())
                        .build())
                .build();
    }

    private Map<String, Object> attribute(String name,
                                          String type,
                                          boolean required,
                                          boolean multiValued,
                                          String mutability) {
        Map<String, Object> attribute = new LinkedHashMap<>();
        attribute.put("name", name);
        attribute.put("type", type);
        attribute.put("required", required);
        attribute.put("multiValued", multiValued);
        attribute.put("mutability", mutability);
        attribute.put("returned", "default");
        return attribute;
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

    private void validateAllowedEmailDomain(TenantAuthConfigEntity config,
                                            String email) {
        List<String> domains = splitCsv(config.getAllowedEmailDomains()).stream()
                .map(item -> item.toLowerCase(Locale.ROOT))
                .toList();
        if (domains.isEmpty()) {
            return;
        }
        int atIndex = email.indexOf('@');
        if (atIndex < 0) {
            throw new IllegalArgumentException("Identity email domain is not allowed for this tenant");
        }
        String domain = email.substring(atIndex + 1).toLowerCase(Locale.ROOT);
        if (!domains.contains(domain)) {
            throw new IllegalArgumentException("Identity email domain is not allowed for this tenant");
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
                .entityType("SCIM_USER")
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

    private String normalizePatchPath(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
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

    private boolean asBoolean(Object value,
                              String label) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String stringValue = trimToNull(Objects.toString(value, null));
        if ("true".equalsIgnoreCase(stringValue)) {
            return true;
        }
        if ("false".equalsIgnoreCase(stringValue)) {
            return false;
        }
        throw new IllegalArgumentException(label + " must be true or false");
    }

    private String buildName(String givenName,
                             String familyName) {
        String combined = (defaultIfBlank(givenName, "") + " " + defaultIfBlank(familyName, "")).trim();
        return trimToNull(combined);
    }

    private String toIso(LocalDateTime value) {
        return value == null
                ? null
                : value.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private boolean equalsIgnoreCase(String left,
                                     String right) {
        return trimToNull(left) != null && trimToNull(right) != null && left.equalsIgnoreCase(right);
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

    private record ScimFilterSpec(String attribute, String value) {
    }

    private record ResolvedScimUserInput(
            String externalId,
            String userName,
            String email,
            String displayName,
            String givenName,
            String familyName,
            boolean active,
            boolean groupsProvided,
            Set<String> groups) {

        private String fullName() {
            String combined = displayName;
            if (combined == null) {
                combined = (Objects.toString(givenName, "") + " " + Objects.toString(familyName, "")).trim();
            }
            String normalized = combined == null ? null : combined.trim();
            return normalized == null || normalized.isEmpty() ? userName : normalized;
        }
    }

    private static final class MutableScimUserInput {
        private String externalId;
        private String userName;
        private String email;
        private String displayName;
        private String givenName;
        private String familyName;
        private boolean active;
        private boolean groupsProvided;
        private Set<String> groups = new LinkedHashSet<>();

        private static MutableScimUserInput fromUser(User user) {
            MutableScimUserInput input = new MutableScimUserInput();
            input.externalId = user.getDirectoryExternalId();
            input.userName = user.getUsername();
            input.email = user.getEmail();
            input.displayName = user.getFullName();
            input.active = user.isActive();
            return input;
        }

        private ResolvedScimUserInput toRecord() {
            String combinedName = displayName;
            if (combinedName == null) {
                combinedName = (Objects.toString(givenName, "") + " " + Objects.toString(familyName, "")).trim();
            }
            return new ResolvedScimUserInput(
                    externalId,
                    userName,
                    email,
                    combinedName,
                    givenName,
                    familyName,
                    active,
                    groupsProvided,
                    groups == null ? Set.of() : groups);
        }
    }
}
