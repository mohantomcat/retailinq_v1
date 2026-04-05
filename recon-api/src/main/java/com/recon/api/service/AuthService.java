package com.recon.api.service;

import com.recon.api.domain.*;
import com.recon.api.repository.UserRepository;
import com.recon.api.repository.TenantAuthConfigRepository;
import com.recon.api.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AuditLedgerService auditLedgerService;
    private final TenantAccessAdministrationService tenantAccessAdministrationService;
    private final TenantAuthConfigRepository tenantAuthConfigRepository;
    private final AccessScopeService accessScopeService;
    private final ReconModuleService reconModuleService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        TenantAuthConfigEntity authConfig = tenantAuthConfigRepository.findById(request.getTenantId()).orElse(null);
        if (authConfig != null && !authConfig.isLocalLoginEnabled()) {
            throw new RuntimeException("Local login is disabled for this tenant");
        }

        User user = userRepository
                .findByUsernameAndTenantId(
                        request.getUsername(),
                        request.getTenantId())
                .orElseThrow(() ->
                        new RuntimeException(
                                "Invalid username or password"));

        if (!user.isActive()) {
            throw new RuntimeException(
                    "Account is deactivated");
        }

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash())) {
            throw new RuntimeException(
                    "Invalid username or password");
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(user.getTenantId())
                .sourceType("SECURITY")
                .moduleKey("SECURITY")
                .entityType("USER_SESSION")
                .entityKey(user.getId().toString())
                .actionType("LOGIN_SUCCESS")
                .title("User login successful")
                .summary(user.getUsername())
                .actor(user.getUsername())
                .status("SUCCESS")
                .referenceKey(user.getId().toString())
                .controlFamily("SOX")
                .evidenceTags(List.of("SECURITY", "LOGIN"))
                .build());

        reconModuleService.getAllActiveModules();
        user = userRepository.findById(user.getId()).orElse(user);
        Set<String> permissions = user.getAllPermissions();
        AccessScopeSummaryDto scopeSummary = accessScopeService.summarizeUserScope(user);
        Set<String> storeIds = new java.util.LinkedHashSet<>(scopeSummary.getEffectiveStoreIds());

        String accessToken = tokenProvider.generateAccessToken(
                user.getId().toString(),
                user.getUsername(),
                user.getTenantId(),
                permissions,
                storeIds,
                scopeSummary.isAllStoreAccess(),
                "PASSWORD");

        String refreshToken = tokenProvider.generateRefreshToken(
                user.getId().toString());

        List<RoleDto> roles = user.getRoles().stream()
                .map(r -> RoleDto.builder()
                        .id(r.getId())
                        .name(r.getName())
                        .description(r.getDescription())
                        .permissionCodes(
                                r.getPermissions().stream()
                                        .map(Permission::getCode)
                                        .collect(Collectors.toSet()))
                        .build())
                .collect(Collectors.toList());

        log.info("User {} logged in with {} permissions",
                user.getUsername(), permissions.size());

        List<ReconModuleDto> accessibleModules = reconModuleService.getAccessibleModules(user.getTenantId(), permissions);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(
                        tokenProvider.getExpirationMs() / 1000)
                .userId(user.getId().toString())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .tenantId(user.getTenantId())
                .permissions(permissions)
                .storeIds(user.getStoreIds())
                .effectiveStoreIds(storeIds)
                .allStoreAccess(scopeSummary.isAllStoreAccess())
                .accessScope(scopeSummary)
                .authMode("PASSWORD")
                .roles(roles)
                .accessibleModules(accessibleModules)
                .build();
    }

    public LoginResponse refresh(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException(
                    "Invalid refresh token");
        }

        String userId = tokenProvider
                .getUserIdFromToken(refreshToken);

        User user = userRepository
                .findById(UUID.fromString(userId))
                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"));

        reconModuleService.getAllActiveModules();
        user = userRepository.findById(user.getId()).orElse(user);
        Set<String> permissions = user.getAllPermissions();
        AccessScopeSummaryDto scopeSummary = accessScopeService.summarizeUserScope(user);

        String newAccessToken =
                tokenProvider.generateAccessToken(
                        user.getId().toString(),
                        user.getUsername(),
                        user.getTenantId(),
                        permissions,
                        new java.util.LinkedHashSet<>(scopeSummary.getEffectiveStoreIds()),
                        scopeSummary.isAllStoreAccess(),
                        "PASSWORD");

        List<ReconModuleDto> accessibleModules = reconModuleService.getAccessibleModules(user.getTenantId(), permissions);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .expiresIn(
                        tokenProvider.getExpirationMs() / 1000)
                .permissions(permissions)
                .storeIds(user.getStoreIds())
                .effectiveStoreIds(new java.util.LinkedHashSet<>(scopeSummary.getEffectiveStoreIds()))
                .allStoreAccess(scopeSummary.isAllStoreAccess())
                .accessScope(scopeSummary)
                .authMode("PASSWORD")
                .accessibleModules(accessibleModules)
                .build();
    }

    public UserDto getCurrentUser(String userId) {
        User user = userRepository
                .findById(UUID.fromString(userId))
                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"));
        return toUserDto(user);
    }

    @Transactional
    public UserDto updateProfile(String userId,
                                 UpdateProfileRequest request) {
        User user = userRepository
                .findById(UUID.fromString(userId))
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
        User before = User.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .tenantId(user.getTenantId())
                .active(user.isActive())
                .build();

        String fullName = request != null
                ? trimToNull(request.getFullName())
                : null;
        String email = request != null
                ? trimToNull(request.getEmail())
                : null;

        if (fullName == null) {
            throw new RuntimeException("Full name is required");
        }

        if (email == null) {
            throw new RuntimeException("Email is required");
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new RuntimeException("Enter a valid email address");
        }

        if (!email.equalsIgnoreCase(user.getEmail())
                && userRepository.existsByTenantIdAndEmailIgnoreCase(user.getTenantId(), email)) {
            throw new RuntimeException("Email already exists");
        }

        user.setFullName(fullName);
        user.setEmail(email);

        User saved = userRepository.save(user);
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(saved.getTenantId())
                .sourceType("SECURITY")
                .moduleKey("SECURITY")
                .entityType("USER_PROFILE")
                .entityKey(saved.getId().toString())
                .actionType("PROFILE_UPDATED")
                .title("User profile updated")
                .summary(saved.getUsername())
                .actor(saved.getUsername())
                .status("UPDATED")
                .referenceKey(saved.getId().toString())
                .controlFamily("SOX")
                .evidenceTags(List.of("SECURITY", "PROFILE"))
                .beforeState(before)
                .afterState(saved)
                .build());

        log.info("Profile updated for user {}", saved.getUsername());

        return toUserDto(saved);
    }

    @Transactional
    public void changePassword(String userId,
                               ChangePasswordRequest request) {
        User user = userRepository
                .findById(UUID.fromString(userId))
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        String currentPassword = request != null
                ? trimToNull(request.getCurrentPassword())
                : null;
        String newPassword = request != null
                ? trimToNull(request.getNewPassword())
                : null;

        if (currentPassword == null) {
            throw new RuntimeException("Current password is required");
        }

        if (newPassword == null) {
            throw new RuntimeException("New password is required");
        }

        if (newPassword.length() < 8) {
            throw new RuntimeException(
                    "New password must be at least 8 characters");
        }

        if (!passwordEncoder.matches(
                currentPassword, user.getPasswordHash())) {
            throw new RuntimeException(
                    "Current password is incorrect");
        }

        if (passwordEncoder.matches(
                newPassword, user.getPasswordHash())) {
            throw new RuntimeException(
                    "New password must be different from current password");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(user.getTenantId())
                .sourceType("SECURITY")
                .moduleKey("SECURITY")
                .entityType("USER_PASSWORD")
                .entityKey(user.getId().toString())
                .actionType("PASSWORD_CHANGED")
                .title("User password changed")
                .summary(user.getUsername())
                .actor(user.getUsername())
                .status("UPDATED")
                .referenceKey(user.getId().toString())
                .controlFamily("SOX")
                .evidenceTags(List.of("SECURITY", "PASSWORD"))
                .afterState(java.util.Map.of("passwordChanged", true))
                .build());

        log.info("Password changed for user {}", user.getUsername());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private UserDto toUserDto(User user) {
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
                .effectiveStoreIds(new java.util.LinkedHashSet<>(scopeSummary.getEffectiveStoreIds()))
                .allStoreAccess(scopeSummary.isAllStoreAccess())
                .accessScope(scopeSummary)
                .permissions(permissions)
                .accessibleModules(reconModuleService.getAccessibleModules(currentUser.getTenantId(), permissions))
                .roles(currentUser.getRoles().stream()
                        .map(r -> RoleDto.builder()
                                .id(r.getId())
                                .name(r.getName())
                                .description(
                                        r.getDescription())
                                .permissionCodes(
                                        r.getPermissions()
                                                .stream()
                                                .map(Permission::getCode)
                                                .collect(
                                                        Collectors.toSet()))
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    public LoginOptionsResponse getLoginOptions(String tenantId) {
        return tenantAccessAdministrationService.getLoginOptions(tenantId);
    }
}
