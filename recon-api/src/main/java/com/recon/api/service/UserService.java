package com.recon.api.service;

import com.recon.api.domain.*;
import com.recon.api.repository.RoleRepository;
import com.recon.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLedgerService auditLedgerService;
    private final AccessScopeService accessScopeService;
    private final ReconModuleService reconModuleService;

    private static final int DEFAULT_ACCESS_REVIEW_INTERVAL_DAYS = 90;

    public List<UserDto> getAllUsers(String tenantId) {
        return userRepository.findByTenantId(tenantId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public UserDto getUserById(String tenantId, UUID id) {
        return userRepository.findByIdAndTenantId(id, tenantId)
                .map(this::toDto)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
    }

    @Transactional
    public UserDto createUser(String tenantId, CreateUserRequest req, String actorUsername) {
        if (userRepository.existsByTenantIdAndUsernameIgnoreCase(tenantId, req.getUsername())) {
            throw new RuntimeException(
                    "Username already exists");
        }
        if (userRepository.existsByTenantIdAndEmailIgnoreCase(tenantId, req.getEmail())) {
            throw new RuntimeException(
                    "Email already exists");
        }

        Set<Role> roles = req.getRoleIds() != null
                ? new HashSet<>(roleRepository
                .findAllById(req.getRoleIds()).stream()
                .filter(role -> tenantId.equals(role.getTenantId()))
                .toList())
                : new HashSet<>();
        String identityProvider = normalizeIdentityProvider(req.getIdentityProvider());
        String externalSubject = normalizeExternalSubject(identityProvider, req.getExternalSubject());
        ensureExternalSubjectAvailable(tenantId, identityProvider, externalSubject, null);
        String password = trimToNull(req.getPassword());
        if ("LOCAL".equals(identityProvider) && password == null) {
            throw new RuntimeException("Password is required for local users");
        }

        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(password != null
                        ? password
                        : UUID.randomUUID().toString()))
                .fullName(req.getFullName())
                .tenantId(tenantId)
                .identityProvider(identityProvider)
                .externalSubject(externalSubject)
                .emailVerified(Boolean.TRUE.equals(req.getEmailVerified()))
                .accessReviewStatus("PENDING")
                .accessReviewDueAt(LocalDateTime.now().plusDays(DEFAULT_ACCESS_REVIEW_INTERVAL_DAYS))
                .roles(roles)
                .storeIds(req.getStoreIds() != null
                        ? normalizeStores(req.getStoreIds()) : new HashSet<>())
                .active(true)
                .build();

        User saved = userRepository.save(user);
        recordSecurityAudit(saved.getTenantId(), "USER_CREATED", "User created", saved.getId().toString(), actorUsername, null, saved);
        return toDto(saved);
    }

    @Transactional
    public UserDto updateUser(String tenantId, UUID id, CreateUserRequest req, String actorUsername) {
        User user = userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
        User before = cloneForAudit(user);

        if (req.getFullName() != null)
            user.setFullName(req.getFullName());

        if (req.getEmail() != null
                && !req.getEmail().isBlank()
                && !req.getEmail().equalsIgnoreCase(user.getEmail())) {

            boolean duplicateEmail = userRepository.findByTenantId(tenantId).stream()
                    .anyMatch(existing -> !existing.getId().equals(id)
                            && existing.getEmail() != null
                            && existing.getEmail().equalsIgnoreCase(req.getEmail()));
            if (duplicateEmail) {
                throw new RuntimeException("Email already exists");
            }
            user.setEmail(req.getEmail());
            user.setEmailVerified(false);
        }

        if (req.getPassword() != null
                && !req.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(
                    req.getPassword()));
        }
        updateIdentityBinding(tenantId, user, req);

        User saved = userRepository.save(user);
        recordSecurityAudit(saved.getTenantId(), "USER_UPDATED", "User updated", saved.getId().toString(), actorUsername, before, saved);
        return toDto(saved);
    }

    @Transactional
    public void deactivateUser(String tenantId, UUID id, String actorUsername) {
        User user = userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
        User before = cloneForAudit(user);
        user.setActive(false);
        User saved = userRepository.save(user);
        recordSecurityAudit(saved.getTenantId(), "USER_DEACTIVATED", "User deactivated", saved.getId().toString(), actorUsername, before, saved);
    }

    @Transactional
    public UserDto activateUser(String tenantId, UUID id, String actorUsername) {
        User user = userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
        User before = cloneForAudit(user);
        user.setActive(true);
        User saved = userRepository.save(user);
        recordSecurityAudit(saved.getTenantId(), "USER_ACTIVATED", "User activated", saved.getId().toString(), actorUsername, before, saved);
        return toDto(saved);
    }

    @Transactional
    public void resetPassword(String tenantId, UUID id, ResetPasswordRequest req, String actorUsername) {
        User user = userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        String newPassword = req != null ? req.getNewPassword() : null;

        if (newPassword == null || newPassword.isBlank()) {
            throw new RuntimeException("New password is required");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        recordSecurityAudit(user.getTenantId(), "USER_PASSWORD_RESET", "User password reset", user.getId().toString(), actorUsername, null, java.util.Map.of("passwordReset", true));
    }

    @Transactional
    public void deleteUser(String tenantId, UUID id, String actorUsername) {
        User user = userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
        User before = cloneForAudit(user);
        userRepository.delete(user);
        recordSecurityAudit(user.getTenantId(), "USER_DELETED", "User deleted", user.getId().toString(), actorUsername, before, null);
    }

    @Transactional
    public UserDto assignRoles(String tenantId,
                               UUID userId,
                               AssignRolesRequest req,
                               String actorUsername) {
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
        User before = cloneForAudit(user);
        Set<Role> roles = new HashSet<>(
                roleRepository.findAllById(req.getRoleIds()).stream()
                        .filter(role -> tenantId.equals(role.getTenantId()))
                        .toList());
        user.setRoles(roles);
        User saved = userRepository.save(user);
        recordSecurityAudit(saved.getTenantId(), "USER_ROLES_ASSIGNED", "User roles assigned", saved.getId().toString(), actorUsername, before, saved);
        return toDto(saved);
    }

    @Transactional
    public UserDto assignStores(String tenantId,
                                UUID userId,
                                AssignStoresRequest req,
                                String actorUsername) {
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
        User before = cloneForAudit(user);
        user.setStoreIds(req.getStoreIds() != null
                ? normalizeStores(req.getStoreIds())
                : new HashSet<>());
        User saved = userRepository.save(user);
        recordSecurityAudit(saved.getTenantId(), "USER_STORES_ASSIGNED", "User store scope updated", saved.getId().toString(), actorUsername, before, saved);
        return toDto(saved);
    }

    @Transactional
    public UserDto reviewUserAccess(String tenantId,
                                    UUID userId,
                                    ReviewUserAccessRequest req,
                                    String actorUsername) {
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
        User before = cloneForAudit(user);
        ReviewUserAccessRequest safeRequest = req != null ? req : new ReviewUserAccessRequest();
        String decision = normalizeReviewDecision(safeRequest.getDecision());
        int nextReviewDays = resolveNextReviewDays(decision, safeRequest.getNextReviewInDays());
        LocalDateTime now = LocalDateTime.now();

        user.setAccessReviewStatus(decision);
        user.setLastAccessReviewAt(now);
        user.setLastAccessReviewBy(defaultActor(actorUsername));
        user.setAccessReviewDueAt(now.plusDays(nextReviewDays));
        if (Boolean.TRUE.equals(safeRequest.getDeactivateUser())) {
            user.setActive(false);
        }

        User saved = userRepository.save(user);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("user", cloneForAudit(saved));
        after.put("decision", decision);
        after.put("notes", trimToNull(safeRequest.getNotes()));
        after.put("nextReviewInDays", nextReviewDays);
        after.put("deactivated", Boolean.TRUE.equals(safeRequest.getDeactivateUser()));

        recordSecurityAudit(saved.getTenantId(),
                "USER_ACCESS_REVIEWED",
                "User access reviewed",
                saved.getId().toString(),
                actorUsername,
                before,
                after);
        return toDto(saved);
    }

    private void recordSecurityAudit(String tenantId,
                                     String actionType,
                                     String title,
                                     String entityKey,
                                     String actorUsername,
                                     Object before,
                                     Object after) {
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("SECURITY")
                .moduleKey("SECURITY")
                .entityType("USER")
                .entityKey(entityKey)
                .actionType(actionType)
                .title(title)
                .actor(actorUsername)
                .status(actionType)
                .referenceKey(entityKey)
                .controlFamily("SOX")
                .evidenceTags(List.of("SECURITY", "USER_ADMIN"))
                .beforeState(before)
                .afterState(after)
                .build());
    }

    private User cloneForAudit(User user) {
        return User.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .tenantId(user.getTenantId())
                .identityProvider(user.getIdentityProvider())
                .externalSubject(user.getExternalSubject())
                .emailVerified(user.isEmailVerified())
                .accessReviewStatus(user.getAccessReviewStatus())
                .lastAccessReviewAt(user.getLastAccessReviewAt())
                .lastAccessReviewBy(user.getLastAccessReviewBy())
                .accessReviewDueAt(user.getAccessReviewDueAt())
                .roles(user.getRoles() != null ? new HashSet<>(user.getRoles()) : new HashSet<>())
                .storeIds(user.getStoreIds() != null ? new HashSet<>(user.getStoreIds()) : new HashSet<>())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .build();
    }

    private UserDto toDto(User user) {
        reconModuleService.getAllActiveModules();
        User currentUser = userRepository.findById(user.getId()).orElse(user);
        AccessScopeSummaryDto scopeSummary = accessScopeService.summarizeUserScope(currentUser);
        Set<String> permissions = currentUser.getAllPermissions();
        return UserDto.builder()
                .id(currentUser.getId())
                .username(currentUser.getUsername())
                .email(currentUser.getEmail())
                .fullName(currentUser.getFullName())
                .tenantId(currentUser.getTenantId())
                .active(currentUser.isActive())
                .createdAt(currentUser.getCreatedAt())
                .lastLogin(currentUser.getLastLogin())
                .identityProvider(defaultIfBlank(currentUser.getIdentityProvider(), "LOCAL"))
                .externalSubject(currentUser.getExternalSubject())
                .emailVerified(currentUser.isEmailVerified())
                .accessReviewStatus(defaultIfBlank(currentUser.getAccessReviewStatus(), "PENDING"))
                .lastAccessReviewAt(currentUser.getLastAccessReviewAt())
                .lastAccessReviewBy(currentUser.getLastAccessReviewBy())
                .accessReviewDueAt(currentUser.getAccessReviewDueAt())
                .storeIds(currentUser.getStoreIds())
                .effectiveStoreIds(new HashSet<>(scopeSummary.getEffectiveStoreIds()))
                .allStoreAccess(scopeSummary.isAllStoreAccess())
                .accessScope(scopeSummary)
                .permissions(permissions)
                .accessibleModules(reconModuleService.getAccessibleModules(currentUser.getTenantId(), permissions))
                .roles(currentUser.getRoles().stream()
                        .map(r -> RoleDto.builder()
                                .id(r.getId())
                                .name(r.getName())
                                .description(r.getDescription())
                                .permissionCodes(r.getPermissions()
                                        .stream()
                                        .map(Permission::getCode)
                                        .collect(Collectors.toSet()))
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    private Set<String> normalizeStores(Set<String> storeIds) {
        return storeIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toUpperCase(java.util.Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private void updateIdentityBinding(String tenantId, User user, CreateUserRequest req) {
        if (req.getIdentityProvider() == null
                && req.getExternalSubject() == null
                && req.getEmailVerified() == null) {
            return;
        }
        String identityProvider = req.getIdentityProvider() != null
                ? normalizeIdentityProvider(req.getIdentityProvider())
                : defaultIfBlank(user.getIdentityProvider(), "LOCAL");
        String externalSubject = req.getExternalSubject() != null
                ? trimToNull(req.getExternalSubject())
                : trimToNull(user.getExternalSubject());
        externalSubject = normalizeExternalSubject(identityProvider, externalSubject);
        ensureExternalSubjectAvailable(tenantId, identityProvider, externalSubject, user.getId());
        user.setIdentityProvider(identityProvider);
        user.setExternalSubject(externalSubject);
        if (req.getEmailVerified() != null) {
            user.setEmailVerified(req.getEmailVerified());
        }
    }

    private String normalizeIdentityProvider(String value) {
        String normalized = defaultIfBlank(value, "LOCAL").toUpperCase(Locale.ROOT);
        if (!Set.of("LOCAL", "OIDC", "SAML").contains(normalized)) {
            throw new RuntimeException("Identity provider must be LOCAL, OIDC, or SAML");
        }
        return normalized;
    }

    private String normalizeExternalSubject(String identityProvider, String externalSubject) {
        String normalized = trimToNull(externalSubject);
        if ("LOCAL".equals(identityProvider)) {
            return null;
        }
        if (normalized == null) {
            throw new RuntimeException("External subject is required for SSO users");
        }
        return normalized;
    }

    private void ensureExternalSubjectAvailable(String tenantId,
                                                String identityProvider,
                                                String externalSubject,
                                                UUID currentUserId) {
        if (externalSubject == null) {
            return;
        }
        userRepository.findByTenantIdAndIdentityProviderIgnoreCaseAndExternalSubjectIgnoreCase(
                        tenantId, identityProvider, externalSubject)
                .filter(existing -> !Objects.equals(existing.getId(), currentUserId))
                .ifPresent(existing -> {
                    throw new RuntimeException("External subject is already mapped to another user");
                });
    }

    private String normalizeReviewDecision(String decision) {
        String normalized = defaultIfBlank(decision, "CERTIFIED").toUpperCase(Locale.ROOT);
        if (!Set.of("CERTIFIED", "NEEDS_CHANGES", "REVOKE_REQUESTED").contains(normalized)) {
            throw new RuntimeException("Access review decision must be CERTIFIED, NEEDS_CHANGES, or REVOKE_REQUESTED");
        }
        return normalized;
    }

    private int resolveNextReviewDays(String decision, Integer requestedDays) {
        int fallback = "CERTIFIED".equals(decision) ? DEFAULT_ACCESS_REVIEW_INTERVAL_DAYS : 14;
        int resolved = requestedDays == null ? fallback : requestedDays;
        if (resolved < 1 || resolved > 365) {
            throw new RuntimeException("Next access review must be between 1 and 365 days");
        }
        return resolved;
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
        return defaultIfBlank(actor, "system");
    }
}
