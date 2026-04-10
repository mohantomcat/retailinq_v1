package com.recon.api.repository;

import com.recon.api.domain.TenantOidcGroupRoleMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TenantOidcGroupRoleMappingRepository
        extends JpaRepository<TenantOidcGroupRoleMapping, UUID> {

    List<TenantOidcGroupRoleMapping> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    List<TenantOidcGroupRoleMapping> findByTenantIdAndActiveTrue(String tenantId);

    void deleteByTenantId(String tenantId);
}
