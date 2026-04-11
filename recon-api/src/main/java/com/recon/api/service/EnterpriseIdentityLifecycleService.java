package com.recon.api.service;

import com.recon.api.domain.AuditLedgerWriteRequest;
import com.recon.api.domain.Role;
import com.recon.api.domain.TenantAuthConfigEntity;
import com.recon.api.domain.TenantOidcGroupRoleMapping;
import com.recon.api.domain.User;
import com.recon.api.repository.TenantOidcGroupRoleMappingRepository;
import com.recon.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
public class EnterpriseIdentityLifecycleService {

    private static final int DEFAULT_ACCESS_REVIEW_INTERVAL_DAYS = 90;

    private final UserRepository userRepository;
    private final TenantOidcGroupRoleMappingRepository tenantOidcGroupRoleMappingRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLedgerService auditLedgerService;

    @Transactional
    public User syncSsoIdentity(TenantAuthConfigEntity config,
                                String identityProvider,
                                ExternalIdentityProfile profile,
                                String actor) {
        String tenantId = config.getTenantId();
        String normalizedProvider = normalizeIdentityProvider(identityProvider);
        String externalSubject = requireField(profile.externalSubject(), "External subject");
        String email = requireField(profile.email(), "Email");
        validateAllowedEmailDomain(config, email);

        User user = userRepository
                .findByTenantIdAndIdentityProviderIgnoreCaseAndExternalSubjectIgnoreCase(
                        tenantId,
                        normalizedProvider,
                        externalSubject)
                .orElse(null);
        boolean newUser = false;
        if (user == null) {
            user = userRepository.findByTenantIdAndEmailIgnoreCase(tenantId, email).orElse(null);
            if (user != null
                    && trimToNull(user.getExternalSubject()) != null
                    && !externalSubject.equalsIgnoreCase(user.getExternalSubject())
                    && normalizedProvider.equalsIgnoreCase(defaultIfBlank(user.getIdentityProvider(), normalizedProvider))) {
                throw new IllegalArgumentException("External subject is already linked to another user");
            }
        }

        if (user == null) {
            if (!config.isAutoProvisionUsers()) {
                throw new IllegalArgumentException("SSO user is not provisioned for this tenant");
            }
            user = User.builder()
                    .tenantId(tenantId)
                    .username(nextAvailableUsername(tenantId, profile.username()))
                    .email(email)
                    .fullName(defaultIfBlank(profile.fullName(), profile.username()))
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .identityProvider(normalizedProvider)
                    .externalSubject(externalSubject)
                    .emailVerified(profile.emailVerified())
                    .accessReviewStatus("PENDING")
                    .accessReviewDueAt(LocalDateTime.now().plusDays(DEFAULT_ACCESS_REVIEW_INTERVAL_DAYS))
                    .active(true)
                    .build();
            newUser = true;
        } else {
            if (!user.isActive()) {
                throw new IllegalArgumentException("Account is deactivated");
            }
            if (!email.equalsIgnoreCase(user.getEmail())
                    && userRepository.existsByTenantIdAndEmailIgnoreCase(tenantId, email)) {
                throw new IllegalArgumentException("SSO email is already assigned to another user");
            }
            user.setEmail(email);
            user.setFullName(defaultIfBlank(profile.fullName(), user.getFullName()));
            user.setIdentityProvider(normalizedProvider);
            user.setExternalSubject(externalSubject);
            user.setEmailVerified(profile.emailVerified());
        }

        syncMappedRoles(tenantId, user, profile.groups(), normalizedProvider, actor, true, !newUser);
        User saved = userRepository.save(user);
        if (newUser) {
            recordAudit(saved.getTenantId(),
                    normalizedProvider + "_USER_PROVISIONED",
                    normalizedProvider + " user provisioned",
                    saved.getId().toString(),
                    actor,
                    Map.of(
                            "identityProvider", normalizedProvider,
                            "externalSubject", externalSubject,
                            "email", email));
        }
        return saved;
    }

    @Transactional
    public User syncScimIdentity(TenantAuthConfigEntity config,
                                 ScimIdentityProfile profile,
                                 String actor) {
        String tenantId = config.getTenantId();
        String directoryExternalId = trimToNull(profile.directoryExternalId());
        String email = trimToNull(profile.email());
        if (email != null) {
            validateAllowedEmailDomain(config, email);
        }

        User user = resolveScimUser(tenantId, directoryExternalId, email, profile.username());
        boolean newUser = false;
        if (user == null) {
            String username = nextAvailableUsername(tenantId, profile.username());
            user = User.builder()
                    .tenantId(tenantId)
                    .username(username)
                    .email(defaultIfBlank(email, username + "@provisioned.invalid"))
                    .fullName(firstNonBlank(profile.fullName(), profile.username(), username))
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .identityProvider("SCIM")
                    .externalSubject(firstNonBlank(directoryExternalId, profile.username(), username))
                    .directoryExternalId(firstNonBlank(directoryExternalId, profile.username(), username))
                    .emailVerified(email != null)
                    .accessReviewStatus("PENDING")
                    .accessReviewDueAt(LocalDateTime.now().plusDays(DEFAULT_ACCESS_REVIEW_INTERVAL_DAYS))
                    .active(profile.active())
                    .build();
            newUser = true;
        } else {
            if (email != null
                    && !email.equalsIgnoreCase(user.getEmail())
                    && userRepository.existsByTenantIdAndEmailIgnoreCase(tenantId, email)) {
                throw new IllegalArgumentException("SCIM email is already assigned to another user");
            }
            if (directoryExternalId != null) {
                ensureDirectoryExternalIdAvailable(tenantId, directoryExternalId, user.getId());
                user.setDirectoryExternalId(directoryExternalId);
                if (trimToNull(user.getExternalSubject()) == null) {
                    user.setExternalSubject(directoryExternalId);
                }
            }
            if (trimToNull(profile.username()) != null) {
                user.setUsername(resolveUpdatedUsername(tenantId, user.getId(), profile.username(), user.getUsername()));
            }
            if (email != null) {
                user.setEmail(email);
                user.setEmailVerified(true);
            }
            user.setFullName(firstNonBlank(profile.fullName(), user.getFullName()));
            user.setActive(profile.active());
            if (trimToNull(user.getIdentityProvider()) == null || "LOCAL".equalsIgnoreCase(user.getIdentityProvider())) {
                user.setIdentityProvider(defaultIfBlank(user.getIdentityProvider(), "SCIM"));
            }
        }

        if (trimToNull(directoryExternalId) != null) {
            ensureDirectoryExternalIdAvailable(tenantId, directoryExternalId, user.getId());
        }

        if (profile.groups() != null) {
            syncMappedRoles(tenantId, user, profile.groups(), "SCIM", actor, true, false);
        }

        User saved = userRepository.save(user);
        recordAudit(saved.getTenantId(),
                newUser ? "SCIM_USER_CREATED" : "SCIM_USER_UPDATED",
                newUser ? "SCIM user provisioned" : "SCIM user updated",
                saved.getId().toString(),
                actor,
                Map.of(
                        "directoryExternalId", saved.getDirectoryExternalId(),
                        "active", saved.isActive()));
        return saved;
    }

    public void syncMappedRoles(String tenantId,
                                User user,
                                Set<String> groups,
                                String source,
                                String actor,
                                boolean requireActiveMappings,
                                boolean allowExistingRolesWhenMissingGroups) {
        Set<String> normalizedGroups = normalizeGroups(groups);
        if (normalizedGroups.isEmpty()) {
            if (allowExistingRolesWhenMissingGroups) {
                return;
            }
            throw new IllegalArgumentException(source + " identity did not provide any mapped groups");
        }

        Set<Role> mappedRoles = resolveMappedRoles(tenantId, normalizedGroups, source, requireActiveMappings);
        if (mappedRoles.isEmpty()) {
            throw new IllegalArgumentException("No application role mapping matched the " + source + " groups");
        }

        applyMappedRoles(tenantId, user, mappedRoles, normalizedGroups, source, actor);
    }

    public Set<Role> resolveMappedRoles(String tenantId,
                                        Set<String> groups,
                                        String source,
                                        boolean requireActiveMappings) {
        List<TenantOidcGroupRoleMapping> mappings =
                tenantOidcGroupRoleMappingRepository.findByTenantIdAndActiveTrue(tenantId);
        if (mappings.isEmpty()) {
            if (requireActiveMappings) {
                throw new IllegalArgumentException(
                        "Configure at least one active external group-role mapping before enabling " + source + " access");
            }
            return Set.of();
        }

        Set<String> normalizedGroups = normalizeGroups(groups);
        if (normalizedGroups.isEmpty()) {
            return Set.of();
        }

        return mappings.stream()
                .filter(mapping -> normalizedGroups.contains(normalizeGroup(mapping.getOidcGroup())))
                .map(TenantOidcGroupRoleMapping::getRole)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void applyMappedRoles(String tenantId,
                                 User user,
                                 Set<Role> mappedRoles,
                                 Set<String> groups,
                                 String source,
                                 String actor) {
        Set<String> normalizedGroups = normalizeGroups(groups);
        Set<UUID> beforeRoleIds = user.getRoles() == null
                ? Set.of()
                : user.getRoles().stream().map(Role::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> afterRoleIds = (mappedRoles == null ? Set.<Role>of() : mappedRoles).stream()
                .map(Role::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        user.setRoles(mappedRoles == null ? new LinkedHashSet<>() : new LinkedHashSet<>(mappedRoles));
        if (!Objects.equals(beforeRoleIds, afterRoleIds)) {
            recordAudit(tenantId,
                    source + "_ROLES_SYNCED",
                    source + " roles synced",
                    user.getId() != null ? user.getId().toString() : user.getUsername(),
                    actor,
                    Map.of(
                            "groups", normalizedGroups,
                            "beforeRoleIds", beforeRoleIds,
                            "afterRoleIds", afterRoleIds));
        }
    }

    public Set<String> normalizeGroups(Set<String> groups) {
        return groups == null ? Set.of() : groups.stream()
                .map(this::normalizeGroup)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private User resolveScimUser(String tenantId,
                                 String directoryExternalId,
                                 String email,
                                 String username) {
        User user = directoryExternalId == null
                ? null
                : userRepository.findByTenantIdAndDirectoryExternalIdIgnoreCase(tenantId, directoryExternalId).orElse(null);
        if (user != null) {
            return user;
        }
        user = email == null ? null : userRepository.findByTenantIdAndEmailIgnoreCase(tenantId, email).orElse(null);
        if (user != null) {
            return user;
        }
        return trimToNull(username) == null
                ? null
                : userRepository.findByUsernameAndTenantId(username, tenantId).orElse(null);
    }

    private void validateAllowedEmailDomain(TenantAuthConfigEntity config, String email) {
        List<String> domains = splitCsv(config.getAllowedEmailDomains()).stream()
                .map(item -> item.toLowerCase(Locale.ROOT))
                .toList();
        if (domains.isEmpty()) {
            return;
        }
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT);
        if (!domains.contains(domain)) {
            throw new IllegalArgumentException("Identity email domain is not allowed for this tenant");
        }
    }

    private void ensureDirectoryExternalIdAvailable(String tenantId,
                                                    String directoryExternalId,
                                                    UUID currentUserId) {
        userRepository.findByTenantIdAndDirectoryExternalIdIgnoreCase(tenantId, directoryExternalId)
                .filter(existing -> !Objects.equals(existing.getId(), currentUserId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Directory external id is already linked to another user");
                });
    }

    private String nextAvailableUsername(String tenantId, String preferredUsername) {
        String base = sanitizeUsername(firstNonBlank(preferredUsername, "external-user"));
        String candidate = base;
        int suffix = 2;
        while (userRepository.existsByTenantIdAndUsernameIgnoreCase(tenantId, candidate)) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String resolveUpdatedUsername(String tenantId,
                                          UUID currentUserId,
                                          String requestedUsername,
                                          String currentUsername) {
        String normalized = sanitizeUsername(requestedUsername);
        if (normalized.equalsIgnoreCase(defaultIfBlank(currentUsername, normalized))) {
            return currentUsername;
        }
        userRepository.findByUsernameAndTenantId(normalized, tenantId)
                .filter(existing -> !Objects.equals(existing.getId(), currentUserId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Username already exists");
                });
        return normalized;
    }

    private String sanitizeUsername(String value) {
        String sanitized = defaultIfBlank(value, "external-user")
                .replaceAll("[^A-Za-z0-9._-]", "-")
                .replaceAll("-+", "-");
        sanitized = trimToNull(sanitized);
        if (sanitized == null || "-".equals(sanitized)) {
            return "external-user";
        }
        return sanitized.length() > 120 ? sanitized.substring(0, 120) : sanitized;
    }

    private String normalizeIdentityProvider(String value) {
        String normalized = defaultIfBlank(value, "OIDC").toUpperCase(Locale.ROOT);
        if (!Set.of("OIDC", "SAML", "SCIM").contains(normalized)) {
            throw new IllegalArgumentException("Identity provider must be OIDC, SAML, or SCIM");
        }
        return normalized;
    }

    private String normalizeGroup(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String requireField(String value, String label) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return trimmed;
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

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private void recordAudit(String tenantId,
                             String actionType,
                             String title,
                             String entityKey,
                             String actor,
                             Object metadata) {
        auditLedgerService.record(AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("SECURITY")
                .moduleKey("SECURITY")
                .entityType("EXTERNAL_IDENTITY")
                .entityKey(entityKey)
                .actionType(actionType)
                .title(title)
                .actor(defaultIfBlank(actor, "system"))
                .status(actionType)
                .referenceKey(entityKey)
                .controlFamily("ACCESS_CONTROL")
                .evidenceTags(List.of("SECURITY", "SSO", "SCIM"))
                .metadata(metadata instanceof Map<?, ?> map ? new LinkedHashMap<>(map) : metadata)
                .build());
    }

    public record ExternalIdentityProfile(
            String externalSubject,
            String username,
            String email,
            String fullName,
            boolean emailVerified,
            Set<String> groups) {
    }

    public record ScimIdentityProfile(
            String directoryExternalId,
            String username,
            String email,
            String fullName,
            boolean active,
            Set<String> groups) {
    }
}
