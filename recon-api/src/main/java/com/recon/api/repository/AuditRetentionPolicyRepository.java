package com.recon.api.repository;

import com.recon.api.domain.AuditRetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuditRetentionPolicyRepository extends JpaRepository<AuditRetentionPolicy, UUID> {
    Optional<AuditRetentionPolicy> findByTenantId(String tenantId);
}
