package com.recon.api.service;

import com.recon.api.domain.AuditLedgerEntry;
import com.recon.api.domain.AuditLedgerWriteRequest;
import com.recon.api.domain.EmergencyAccessGrantDto;
import com.recon.api.domain.PrivilegedActionAlertDto;
import com.recon.api.domain.QuarterlyAccessReviewCycleResponse;
import com.recon.api.domain.GrantEmergencyAccessRequest;
import com.recon.api.domain.RevokeEmergencyAccessRequest;
import com.recon.api.domain.Role;
import com.recon.api.domain.RoleDto;
import com.recon.api.domain.User;
import com.recon.api.domain.UserEmergencyAccessGrant;
import com.recon.api.repository.AuditLedgerEntryRepository;
import com.recon.api.repository.RoleRepository;
import com.recon.api.repository.UserEmergencyAccessGrantRepository;
import com.recon.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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
public class PrivilegedAccessService {

    private static final int DEFAULT_EMERGENCY_ACCESS_HOURS = 4;
    private static final int MAX_EMERGENCY_ACCESS_HOURS = 24;

    private final UserEmergencyAccessGrantRepository userEmergencyAccessGrantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditLedgerEntryRepository auditLedgerEntryRepository;
    private final AuditLedgerService auditLedgerService;

    @Transactional
    public EmergencyAccessGrantDto grantEmergencyAccess(String tenantId,
                                                        GrantEmergencyAccessRequest request,
                                                        String actor) {
        GrantEmergencyAccessRequest safeRequest = request != null ? request : new GrantEmergencyAccessRequest();
        User user = userRepository.findByIdAndTenantId(requireUuid(safeRequest.getUserId(), "User"), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!user.isActive()) {
            throw new IllegalArgumentException("Emergency access can only be granted to active users");
        }

        Set<Role> roles = new LinkedHashSet<>(roleRepository.findAllById(
                safeRequest.getRoleIds() == null ? Set.<UUID>of() : safeRequest.getRoleIds()).stream()
                .filter(role -> Objects.equals(role.getTenantId(), tenantId))
                .toList());
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("Select at least one privileged role");
        }
        if (roles.stream().anyMatch(role -> !hasHighPrivilegeRole(role))) {
            throw new IllegalArgumentException("Emergency access only supports privileged roles");
        }

        String justification = requireText(safeRequest.getJustification(), "Justification");
        String approvalNote = requireText(safeRequest.getApprovalNote(), "Approval note");
        int expiresInHours = resolveExpiresInHours(safeRequest.getExpiresInHours());

        UserEmergencyAccessGrant grant = userEmergencyAccessGrantRepository.save(UserEmergencyAccessGrant.builder()
                .tenantId(tenantId)
                .user(user)
                .roles(roles)
                .justification(justification)
                .approvalNote(approvalNote)
                .grantedBy(defaultActor(actor))
                .expiresAt(LocalDateTime.now().plusHours(expiresInHours))
                .build());

        recordPrivilegedAction(tenantId,
                "EMERGENCY_ACCESS_GRANTED",
                "Emergency admin access granted",
                "%s received temporary privileged access".formatted(defaultIfBlank(user.getUsername(), user.getId().toString())),
                defaultActor(actor),
                "EMERGENCY_ACCESS",
                grant.getId().toString(),
                "ACTIVE",
                snapshotGrant(grant),
                Map.of(
                        "severity", "CRITICAL",
                        "expiresInHours", expiresInHours));
        return toDto(grant, true);
    }

    @Transactional
    public EmergencyAccessGrantDto revokeEmergencyAccess(String tenantId,
                                                         UUID grantId,
                                                         RevokeEmergencyAccessRequest request,
                                                         String actor) {
        UserEmergencyAccessGrant grant = userEmergencyAccessGrantRepository.findByIdAndTenantId(
                        requireUuid(grantId, "Emergency access grant"),
                        tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Emergency access grant not found"));
        if (grant.getRevokedAt() != null) {
            throw new IllegalArgumentException("Emergency access grant is already revoked");
        }
        grant.setRevokedAt(LocalDateTime.now());
        grant.setRevokedBy(defaultActor(actor));
        grant.setRevokeNote(requireText(
                request != null ? request.getRevokeNote() : null,
                "Revoke note"));
        UserEmergencyAccessGrant saved = userEmergencyAccessGrantRepository.save(grant);

        recordPrivilegedAction(tenantId,
                "EMERGENCY_ACCESS_REVOKED",
                "Emergency admin access revoked",
                "%s emergency access was revoked".formatted(defaultIfBlank(saved.getUser().getUsername(), saved.getUser().getId().toString())),
                defaultActor(actor),
                "EMERGENCY_ACCESS",
                saved.getId().toString(),
                "REVOKED",
                snapshotGrant(saved),
                Map.of("severity", "HIGH"));
        return toDto(saved, false);
    }

    @Transactional
    public QuarterlyAccessReviewCycleResponse startQuarterlyAccessReviewCycle(String tenantId,
                                                                              String actor) {
        LocalDateTime now = LocalDateTime.now();
        int queuedUsers = 0;
        int alreadyQueuedUsers = 0;
        int usersMissingManager = 0;

        for (User user : userRepository.findByTenantId(tenantId)) {
            if (!user.isActive()) {
                continue;
            }
            if (user.getAccessReviewDueAt() != null && user.getAccessReviewDueAt().isAfter(now)) {
                continue;
            }
            if ("PENDING_MANAGER".equalsIgnoreCase(defaultIfBlank(user.getAccessReviewStatus(), "PENDING"))) {
                alreadyQueuedUsers++;
            } else {
                user.setAccessReviewStatus("PENDING_MANAGER");
                userRepository.save(user);
                queuedUsers++;
            }
            if (user.getManagerUserId() == null) {
                usersMissingManager++;
            }
        }

        QuarterlyAccessReviewCycleResponse response = QuarterlyAccessReviewCycleResponse.builder()
                .queuedUsers(queuedUsers)
                .alreadyQueuedUsers(alreadyQueuedUsers)
                .usersMissingManager(usersMissingManager)
                .build();

        auditLedgerService.record(AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("SECURITY")
                .moduleKey("SECURITY")
                .entityType("ACCESS_REVIEW")
                .entityKey(tenantId)
                .actionType("ACCESS_REVIEW_QUARTERLY_CYCLE_STARTED")
                .title("Quarterly manager access review cycle started")
                .summary("%d users queued for review".formatted(queuedUsers))
                .actor(defaultActor(actor))
                .status("STARTED")
                .referenceKey(tenantId)
                .controlFamily("ACCESS_CONTROL")
                .evidenceTags(List.of("SECURITY", "ACCESS_REVIEW"))
                .afterState(response)
                .build());
        return response;
    }

    @Transactional(readOnly = true)
    public List<EmergencyAccessGrantDto> listEmergencyAccessGrants(String tenantId) {
        return userEmergencyAccessGrantRepository.findByTenantIdOrderByGrantedAtDesc(tenantId).stream()
                .limit(25)
                .map(grant -> toDto(grant, isActiveGrant(grant, LocalDateTime.now())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PrivilegedActionAlertDto> listPrivilegedActionAlerts(String tenantId) {
        return auditLedgerEntryRepository.findTop100ByTenantIdAndSourceTypeOrderByEventAtDesc(tenantId, "SECURITY").stream()
                .filter(this::isPrivilegedAlertEntry)
                .limit(25)
                .map(this::toAlertDto)
                .toList();
    }

    @Transactional
    public ResolvedAccess resolveEffectiveAccess(User user) {
        if (user == null) {
            return new ResolvedAccess(Set.of(), Set.of(), List.of(), false, null);
        }
        expireGrantsForUser(user.getTenantId(), user.getId());
        LocalDateTime now = LocalDateTime.now();
        List<UserEmergencyAccessGrant> activeGrants =
                userEmergencyAccessGrantRepository.findByTenantIdAndUser_IdAndRevokedAtIsNullAndExpiresAtAfterOrderByExpiresAtAsc(
                        user.getTenantId(),
                        user.getId(),
                        now);
        Set<Role> effectiveRoles = new LinkedHashSet<>(user.getRoles() == null ? Set.<Role>of() : user.getRoles());
        Set<Role> emergencyRoles = activeGrants.stream()
                .flatMap(grant -> grant.getRoles().stream())
                .filter(role -> Objects.equals(role.getTenantId(), user.getTenantId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        effectiveRoles.addAll(emergencyRoles);
        Set<String> permissions = effectiveRoles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.getCode())
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LocalDateTime emergencyAccessExpiresAt = activeGrants.stream()
                .map(UserEmergencyAccessGrant::getExpiresAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        return new ResolvedAccess(
                effectiveRoles,
                permissions,
                emergencyRoles.stream().map(this::toRoleDto).toList(),
                !activeGrants.isEmpty(),
                emergencyAccessExpiresAt);
    }

    public boolean hasHighPrivilegeRole(Role role) {
        return role != null
                && role.getPermissions() != null
                && role.getPermissions().stream()
                .map(permission -> permission.getCode())
                .anyMatch(this::isHighPrivilegePermission);
    }

    public boolean hasHighPrivilegeAccess(Set<String> permissions) {
        return permissions != null && permissions.stream().anyMatch(this::isHighPrivilegePermission);
    }

    public void recordPrivilegedRoleChange(String tenantId,
                                           User user,
                                           String actor,
                                           Set<String> beforePermissions,
                                           Set<String> afterPermissions) {
        boolean hadHighPrivilege = hasHighPrivilegeAccess(beforePermissions);
        boolean hasHighPrivilege = hasHighPrivilegeAccess(afterPermissions);
        if (hadHighPrivilege == hasHighPrivilege && Objects.equals(beforePermissions, afterPermissions)) {
            return;
        }
        recordPrivilegedAction(tenantId,
                "PRIVILEGED_ROLE_CHANGE_ALERT",
                "Privileged role assignment changed",
                "%s privileged permissions changed".formatted(defaultIfBlank(user.getUsername(), user.getId().toString())),
                defaultActor(actor),
                "USER",
                user.getId().toString(),
                hasHighPrivilege ? "HIGH" : "UPDATED",
                Map.of(
                        "beforePermissions", beforePermissions,
                        "afterPermissions", afterPermissions),
                Map.of("severity", hasHighPrivilege ? "HIGH" : "MEDIUM"));
    }

    public void recordPrivilegedReviewAlert(String tenantId,
                                            User user,
                                            String actor,
                                            String decision,
                                            boolean deactivated) {
        if (!"REVOKE_REQUESTED".equalsIgnoreCase(defaultIfBlank(decision, ""))
                && !deactivated) {
            return;
        }
        recordPrivilegedAction(tenantId,
                "PRIVILEGED_ACCESS_REVIEW_ALERT",
                "Privileged access review escalation",
                "%s access review requested revoke or deactivation".formatted(defaultIfBlank(user.getUsername(), user.getId().toString())),
                defaultActor(actor),
                "USER",
                user.getId().toString(),
                "HIGH",
                Map.of(
                        "decision", decision,
                        "deactivated", deactivated),
                Map.of("severity", "HIGH"));
    }

    @Transactional
    public void expireGrantsForUser(String tenantId, UUID userId) {
        if (trimToNull(tenantId) == null || userId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        userEmergencyAccessGrantRepository
                .findByTenantIdAndUser_IdAndRevokedAtIsNullAndExpiresAtBeforeOrderByExpiresAtAsc(tenantId, userId, now)
                .forEach(this::expireGrant);
    }

    private void expireGrant(UserEmergencyAccessGrant grant) {
        if (grant == null || grant.getRevokedAt() != null) {
            return;
        }
        grant.setRevokedAt(LocalDateTime.now());
        grant.setRevokedBy("system-expired");
        grant.setRevokeNote("Expired automatically");
        UserEmergencyAccessGrant saved = userEmergencyAccessGrantRepository.save(grant);
        recordPrivilegedAction(saved.getTenantId(),
                "EMERGENCY_ACCESS_EXPIRED",
                "Emergency admin access expired",
                "%s emergency access expired".formatted(defaultIfBlank(
                        saved.getUser() != null ? saved.getUser().getUsername() : null,
                        saved.getId().toString())),
                "system-expired",
                "EMERGENCY_ACCESS",
                saved.getId().toString(),
                "EXPIRED",
                snapshotGrant(saved),
                Map.of("severity", "HIGH"));
    }

    private EmergencyAccessGrantDto toDto(UserEmergencyAccessGrant grant, boolean active) {
        User user = grant.getUser();
        return EmergencyAccessGrantDto.builder()
                .id(grant.getId())
                .userId(user != null ? user.getId() : null)
                .username(user != null ? user.getUsername() : null)
                .fullName(user != null ? firstNonBlank(user.getFullName(), user.getUsername()) : null)
                .roles((grant.getRoles() == null ? Set.<Role>of() : grant.getRoles()).stream()
                        .map(this::toRoleDto)
                        .toList())
                .justification(grant.getJustification())
                .approvalNote(grant.getApprovalNote())
                .grantedBy(grant.getGrantedBy())
                .grantedAt(grant.getGrantedAt())
                .expiresAt(grant.getExpiresAt())
                .active(active)
                .revokedBy(grant.getRevokedBy())
                .revokedAt(grant.getRevokedAt())
                .revokeNote(grant.getRevokeNote())
                .build();
    }

    private RoleDto toRoleDto(Role role) {
        return RoleDto.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .permissionCodes((role.getPermissions() == null ? Set.<String>of() : role.getPermissions().stream()
                        .map(permission -> permission.getCode())
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new))))
                .build();
    }

    private Map<String, Object> snapshotGrant(UserEmergencyAccessGrant grant) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("grantId", grant.getId());
        snapshot.put("tenantId", grant.getTenantId());
        snapshot.put("userId", grant.getUser() != null ? grant.getUser().getId() : null);
        snapshot.put("username", grant.getUser() != null ? grant.getUser().getUsername() : null);
        snapshot.put("roleNames", (grant.getRoles() == null ? Set.<Role>of() : grant.getRoles()).stream()
                .map(Role::getName)
                .filter(Objects::nonNull)
                .sorted()
                .toList());
        snapshot.put("justification", grant.getJustification());
        snapshot.put("approvalNote", grant.getApprovalNote());
        snapshot.put("grantedBy", grant.getGrantedBy());
        snapshot.put("grantedAt", grant.getGrantedAt());
        snapshot.put("expiresAt", grant.getExpiresAt());
        snapshot.put("revokedBy", grant.getRevokedBy());
        snapshot.put("revokedAt", grant.getRevokedAt());
        snapshot.put("revokeNote", grant.getRevokeNote());
        return snapshot;
    }

    private boolean isActiveGrant(UserEmergencyAccessGrant grant, LocalDateTime now) {
        return grant != null
                && grant.getRevokedAt() == null
                && grant.getExpiresAt() != null
                && grant.getExpiresAt().isAfter(now);
    }

    private boolean isPrivilegedAlertEntry(AuditLedgerEntry entry) {
        String actionType = normalizeUpper(entry != null ? entry.getActionType() : null);
        return actionType != null
                && (actionType.startsWith("EMERGENCY_ACCESS_") || actionType.startsWith("PRIVILEGED_"));
    }

    private PrivilegedActionAlertDto toAlertDto(AuditLedgerEntry entry) {
        String actionType = normalizeUpper(entry.getActionType());
        boolean emergencyAction = actionType != null && actionType.startsWith("EMERGENCY_ACCESS_");
        return PrivilegedActionAlertDto.builder()
                .id(entry.getId() != null ? entry.getId().toString() : null)
                .actionType(actionType)
                .title(entry.getTitle())
                .detail(firstNonBlank(entry.getSummary(), entry.getReason(), entry.getAfterState()))
                .severity(emergencyAction ? "CRITICAL" : "HIGH")
                .actor(entry.getActor())
                .entityType(entry.getEntityType())
                .entityKey(entry.getEntityKey())
                .status(entry.getStatus())
                .eventAt(entry.getEventAt())
                .build();
    }

    private void recordPrivilegedAction(String tenantId,
                                        String actionType,
                                        String title,
                                        String summary,
                                        String actor,
                                        String entityType,
                                        String entityKey,
                                        String status,
                                        Object afterState,
                                        Map<String, Object> metadata) {
        auditLedgerService.record(AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("SECURITY")
                .moduleKey("SECURITY")
                .entityType(entityType)
                .entityKey(entityKey)
                .actionType(actionType)
                .title(title)
                .summary(summary)
                .actor(actor)
                .status(status)
                .referenceKey(entityKey)
                .controlFamily("ACCESS_CONTROL")
                .evidenceTags(List.of("SECURITY", "PRIVILEGED_ACCESS"))
                .afterState(afterState)
                .metadata(metadata)
                .build());
    }

    private boolean isHighPrivilegePermission(String permission) {
        String normalized = normalizeUpper(permission);
        return normalized != null
                && (normalized.startsWith("ADMIN_")
                || normalized.endsWith("_MANAGE")
                || Set.of(
                        "AUDIT_EXPORT",
                        "AUDIT_GLOBAL_VIEW",
                        "API_ACCESS_MANAGE",
                        "ACCESS_REVIEW_MANAGE").contains(normalized));
    }

    private int resolveExpiresInHours(Integer expiresInHours) {
        int resolved = expiresInHours == null ? DEFAULT_EMERGENCY_ACCESS_HOURS : expiresInHours;
        if (resolved < 1 || resolved > MAX_EMERGENCY_ACCESS_HOURS) {
            throw new IllegalArgumentException("Emergency access expiration must be between 1 and "
                    + MAX_EMERGENCY_ACCESS_HOURS + " hours");
        }
        return resolved;
    }

    private UUID requireUuid(UUID value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }

    private String requireText(String value, String label) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return trimmed;
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

    private String normalizeUpper(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
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

    public record ResolvedAccess(Set<Role> effectiveRoles,
                                 Set<String> effectivePermissions,
                                 List<RoleDto> emergencyRoles,
                                 boolean emergencyAccessActive,
                                 LocalDateTime emergencyAccessExpiresAt) {
    }
}
