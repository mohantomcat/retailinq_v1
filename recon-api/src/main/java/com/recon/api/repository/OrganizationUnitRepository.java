package com.recon.api.repository;

import com.recon.api.domain.OrganizationUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationUnitRepository extends JpaRepository<OrganizationUnit, UUID> {

    List<OrganizationUnit> findByTenantIdAndActiveTrueOrderBySortOrderAscUnitNameAsc(String tenantId);

    List<OrganizationUnit> findByTenantIdOrderBySortOrderAscUnitNameAsc(String tenantId);

    Optional<OrganizationUnit> findByTenantIdAndUnitKeyIgnoreCase(String tenantId, String unitKey);

    Optional<OrganizationUnit> findByTenantIdAndStoreIdAndActiveTrue(String tenantId, String storeId);

    List<OrganizationUnit> findByTenantIdAndParentUnit_Id(String tenantId, UUID parentUnitId);

    List<OrganizationUnit> findByTenantIdAndUnitTypeIgnoreCaseAndActiveTrueOrderBySortOrderAscUnitNameAsc(String tenantId, String unitType);

    long countByTenantId(String tenantId);

    boolean existsByTenantIdAndUnitKeyIgnoreCase(String tenantId, String unitKey);
}
