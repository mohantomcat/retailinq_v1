package com.recon.api.repository;

import com.recon.api.domain.UserOrganizationScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserOrganizationScopeRepository extends JpaRepository<UserOrganizationScope, UUID> {

    List<UserOrganizationScope> findByTenantIdAndUser_Id(String tenantId, UUID userId);

    void deleteByTenantIdAndUser_Id(String tenantId, UUID userId);

    long countByTenantIdAndOrganizationUnit_Id(String tenantId, UUID organizationUnitId);
}
