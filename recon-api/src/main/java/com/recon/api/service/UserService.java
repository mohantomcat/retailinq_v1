package com.recon.api.service;

import com.recon.api.domain.*;
import com.recon.api.repository.RoleRepository;
import com.recon.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
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

        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(
                        req.getPassword()))
                .fullName(req.getFullName())
                .tenantId(tenantId)
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
        }

        if (req.getPassword() != null
                && !req.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(
                    req.getPassword()));
        }

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
}
