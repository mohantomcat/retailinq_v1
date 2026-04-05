package com.recon.api.repository;

import com.recon.api.domain.TenantSystemEndpointProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantSystemEndpointProfileRepository extends JpaRepository<TenantSystemEndpointProfile, UUID> {

    List<TenantSystemEndpointProfile> findByTenantId(String tenantId);

    Optional<TenantSystemEndpointProfile> findByTenantIdAndSystemNameIgnoreCase(String tenantId, String systemName);

    void deleteByTenantIdAndSystemNameIgnoreCase(String tenantId, String systemName);
}
