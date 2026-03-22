package com.recon.api.service;

import com.recon.api.domain.CreateRoleRequest;
import com.recon.api.domain.Permission;
import com.recon.api.domain.Role;
import com.recon.api.domain.RoleDto;
import com.recon.api.repository.PermissionRepository;
import com.recon.api.repository.RoleRepository;
import com.recon.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final AuditLedgerService auditLedgerService;

    public List<RoleDto> getAllRoles(String tenantId) {
        return roleRepository.findByTenantId(tenantId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<Permission> getAllPermissions() {
        return permissionRepository.findAll();
    }

    @Transactional
    public RoleDto createRole(CreateRoleRequest req, String actorUsername) {
        if (roleRepository.existsByNameAndTenantId(
                req.getName(), req.getTenantId())) {
            throw new RuntimeException("Role already exists");
        }

        Set<Permission> permissions =
                req.getPermissionIds() != null
                        ? new HashSet<>(permissionRepository
                        .findAllById(req.getPermissionIds()))
                        : new HashSet<>();

        Role role = Role.builder()
                .name(req.getName())
                .description(req.getDescription())
                .tenantId(req.getTenantId())
                .permissions(permissions)
                .build();

        Role saved = roleRepository.save(role);
        recordRoleAudit(saved.getTenantId(), "ROLE_CREATED", "Role created", saved.getId().toString(), actorUsername, null, saved);
        return toDto(saved);
    }

    @Transactional
    public RoleDto updateRole(UUID roleId, CreateRoleRequest req, String actorUsername) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() ->
                        new RuntimeException("Role not found"));
        Role before = cloneForAudit(role);

        if (req.getName() == null || req.getName().isBlank()) {
            throw new RuntimeException("Role name is required");
        }

        boolean duplicateExists = roleRepository
                .findByTenantId(role.getTenantId())
                .stream()
                .anyMatch(existing ->
                        !existing.getId().equals(roleId)
                                && existing.getName().equalsIgnoreCase(req.getName()));

        if (duplicateExists) {
            throw new RuntimeException("Role name already exists");
        }

        role.setName(req.getName().trim());
        role.setDescription(req.getDescription());

        Role saved = roleRepository.save(role);
        recordRoleAudit(saved.getTenantId(), "ROLE_UPDATED", "Role updated", saved.getId().toString(), actorUsername, before, saved);
        return toDto(saved);
    }

    @Transactional
    public RoleDto assignPermissions(UUID roleId,
                                     Set<UUID> permissionIds,
                                     String actorUsername) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() ->
                        new RuntimeException("Role not found"));
        Role before = cloneForAudit(role);

        Set<Permission> permissions = new HashSet<>(
                permissionRepository.findAllById(permissionIds));

        role.setPermissions(permissions);
        Role saved = roleRepository.save(role);
        recordRoleAudit(saved.getTenantId(), "ROLE_PERMISSIONS_ASSIGNED", "Role permissions updated", saved.getId().toString(), actorUsername, before, saved);
        return toDto(saved);
    }

    @Transactional
    public void deleteRole(UUID roleId, String actorUsername) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() ->
                        new RuntimeException("Role not found"));

        boolean isAssignedToUsers = userRepository.findAll().stream()
                .anyMatch(user -> user.getRoles() != null
                        && user.getRoles().stream()
                        .anyMatch(r -> roleId.equals(r.getId())));

        if (isAssignedToUsers) {
            throw new RuntimeException(
                    "Role is assigned to one or more users and cannot be deleted");
        }

        Role before = cloneForAudit(role);
        roleRepository.delete(role);
        recordRoleAudit(role.getTenantId(), "ROLE_DELETED", "Role deleted", role.getId().toString(), actorUsername, before, null);
    }

    private void recordRoleAudit(String tenantId,
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
                .entityType("ROLE")
                .entityKey(entityKey)
                .actionType(actionType)
                .title(title)
                .actor(actorUsername)
                .status(actionType)
                .referenceKey(entityKey)
                .controlFamily("SOX")
                .evidenceTags(List.of("SECURITY", "ROLE_ADMIN"))
                .beforeState(before)
                .afterState(after)
                .build());
    }

    private Role cloneForAudit(Role role) {
        return Role.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .tenantId(role.getTenantId())
                .permissions(role.getPermissions() != null ? new HashSet<>(role.getPermissions()) : new HashSet<>())
                .build();
    }

    private RoleDto toDto(Role role) {
        return RoleDto.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .permissionCodes(role.getPermissions()
                        .stream()
                        .map(Permission::getCode)
                        .collect(Collectors.toSet()))
                .build();
    }
}
