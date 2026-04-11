package com.recon.api.repository;

import com.recon.api.domain.UserScimGroupMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface UserScimGroupMembershipRepository extends JpaRepository<UserScimGroupMembership, UUID> {

    List<UserScimGroupMembership> findByTenantId(String tenantId);

    List<UserScimGroupMembership> findByTenantIdAndUser_Id(String tenantId, UUID userId);

    List<UserScimGroupMembership> findByTenantIdAndUser_IdIn(String tenantId, Collection<UUID> userIds);

    List<UserScimGroupMembership> findByTenantIdAndExternalGroupIgnoreCase(String tenantId, String externalGroup);

    void deleteByTenantIdAndUser_Id(String tenantId, UUID userId);

    void deleteByTenantIdAndExternalGroupIgnoreCase(String tenantId, String externalGroup);
}
