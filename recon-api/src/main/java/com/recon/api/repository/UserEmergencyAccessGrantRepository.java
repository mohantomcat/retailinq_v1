package com.recon.api.repository;

import com.recon.api.domain.UserEmergencyAccessGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserEmergencyAccessGrantRepository extends JpaRepository<UserEmergencyAccessGrant, UUID> {

    List<UserEmergencyAccessGrant> findByTenantIdOrderByGrantedAtDesc(String tenantId);

    Optional<UserEmergencyAccessGrant> findByIdAndTenantId(UUID id, String tenantId);

    List<UserEmergencyAccessGrant> findByTenantIdAndRevokedAtIsNullAndExpiresAtAfterOrderByExpiresAtAsc(
            String tenantId,
            LocalDateTime now);

    List<UserEmergencyAccessGrant> findByTenantIdAndRevokedAtIsNullAndExpiresAtBeforeOrderByExpiresAtAsc(
            String tenantId,
            LocalDateTime now);

    List<UserEmergencyAccessGrant> findByTenantIdAndUser_IdAndRevokedAtIsNullAndExpiresAtAfterOrderByExpiresAtAsc(
            String tenantId,
            UUID userId,
            LocalDateTime now);

    List<UserEmergencyAccessGrant> findByTenantIdAndUser_IdAndRevokedAtIsNullAndExpiresAtBeforeOrderByExpiresAtAsc(
            String tenantId,
            UUID userId,
            LocalDateTime now);
}
