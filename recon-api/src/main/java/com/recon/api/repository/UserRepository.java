package com.recon.api.repository;

import com.recon.api.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository
        extends JpaRepository<User, UUID> {

    Optional<User> findByUsernameAndTenantId(
            String username, String tenantId);

    Optional<User> findByUsername(String username);

    List<User> findByTenantId(String tenantId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}