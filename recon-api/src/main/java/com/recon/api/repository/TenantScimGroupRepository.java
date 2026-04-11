package com.recon.api.repository;

import com.recon.api.domain.TenantScimGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantScimGroupRepository extends JpaRepository<TenantScimGroup, UUID> {

    List<TenantScimGroup> findByTenantIdOrderByDisplayNameAsc(String tenantId);

    Optional<TenantScimGroup> findByIdAndTenantId(UUID id, String tenantId);

    Optional<TenantScimGroup> findByTenantIdAndDisplayNameIgnoreCase(String tenantId, String displayName);
}
