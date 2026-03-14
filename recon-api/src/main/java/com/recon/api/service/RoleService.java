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
    public RoleDto createRole(CreateRoleRequest req) {
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

        return toDto(roleRepository.save(role));
    }

    @Transactional
    public RoleDto updateRole(UUID roleId, CreateRoleRequest req) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() ->
                        new RuntimeException("Role not found"));

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

        return toDto(roleRepository.save(role));
    }

    @Transactional
    public RoleDto assignPermissions(UUID roleId,
                                     Set<UUID> permissionIds) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() ->
                        new RuntimeException("Role not found"));

        Set<Permission> permissions = new HashSet<>(
                permissionRepository.findAllById(permissionIds));

        role.setPermissions(permissions);
        return toDto(roleRepository.save(role));
    }

    @Transactional
    public void deleteRole(UUID roleId) {
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

        roleRepository.delete(role);
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