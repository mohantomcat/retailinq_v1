package com.recon.api.repository;

import com.recon.api.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoleRepository
        extends JpaRepository<Role, UUID> {

    List<Role> findByTenantId(String tenantId);

    boolean existsByNameAndTenantId(
            String name, String tenantId);
}