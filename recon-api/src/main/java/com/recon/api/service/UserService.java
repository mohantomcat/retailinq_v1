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

    public List<UserDto> getAllUsers(String tenantId) {
        return userRepository.findByTenantId(tenantId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public UserDto getUserById(UUID id) {
        return userRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
    }

    @Transactional
    public UserDto createUser(CreateUserRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new RuntimeException(
                    "Username already exists");
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new RuntimeException(
                    "Email already exists");
        }

        Set<Role> roles = req.getRoleIds() != null
                ? new HashSet<>(roleRepository
                .findAllById(req.getRoleIds()))
                : new HashSet<>();

        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(
                        req.getPassword()))
                .fullName(req.getFullName())
                .tenantId(req.getTenantId())
                .roles(roles)
                .storeIds(req.getStoreIds() != null
                        ? req.getStoreIds() : new HashSet<>())
                .active(true)
                .build();

        return toDto(userRepository.save(user));
    }

    @Transactional
    public UserDto updateUser(UUID id, CreateUserRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        if (req.getFullName() != null)
            user.setFullName(req.getFullName());

        if (req.getEmail() != null
                && !req.getEmail().isBlank()
                && !req.getEmail().equalsIgnoreCase(user.getEmail())) {

            if (userRepository.existsByEmail(req.getEmail())) {
                throw new RuntimeException("Email already exists");
            }
            user.setEmail(req.getEmail());
        }

        if (req.getPassword() != null
                && !req.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(
                    req.getPassword()));
        }

        return toDto(userRepository.save(user));
    }

    @Transactional
    public void deactivateUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
        user.setActive(false);
        userRepository.save(user);
    }

    @Transactional
    public UserDto activateUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
        user.setActive(true);
        return toDto(userRepository.save(user));
    }

    @Transactional
    public void resetPassword(UUID id, ResetPasswordRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        String newPassword = req != null ? req.getNewPassword() : null;

        if (newPassword == null || newPassword.isBlank()) {
            throw new RuntimeException("New password is required");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
        userRepository.delete(user);
    }

    @Transactional
    public UserDto assignRoles(UUID userId,
                               AssignRolesRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
        Set<Role> roles = new HashSet<>(
                roleRepository.findAllById(req.getRoleIds()));
        user.setRoles(roles);
        return toDto(userRepository.save(user));
    }

    @Transactional
    public UserDto assignStores(UUID userId,
                                AssignStoresRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
        user.setStoreIds(req.getStoreIds() != null
                ? req.getStoreIds()
                : new HashSet<>());
        return toDto(userRepository.save(user));
    }

    private UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .tenantId(user.getTenantId())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .storeIds(user.getStoreIds())
                .permissions(user.getAllPermissions())
                .roles(user.getRoles().stream()
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
}