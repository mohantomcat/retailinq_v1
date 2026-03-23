package com.recon.api.repository;

import com.recon.api.domain.TenantApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantApiKeyRepository extends JpaRepository<TenantApiKey, UUID> {

    List<TenantApiKey> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    Optional<TenantApiKey> findByIdAndTenantId(UUID id, String tenantId);

    Optional<TenantApiKey> findByKeyPrefixAndActiveTrue(String keyPrefix);
}
