package com.recon.api.repository;

import com.recon.api.domain.TenantGroupSelection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantGroupSelectionRepository extends JpaRepository<TenantGroupSelection, UUID> {

    List<TenantGroupSelection> findByTenantId(String tenantId);

    Optional<TenantGroupSelection> findByTenantIdAndGroupCodeIgnoreCase(String tenantId, String groupCode);

    void deleteByTenantIdAndGroupCodeIgnoreCase(String tenantId, String groupCode);
}
