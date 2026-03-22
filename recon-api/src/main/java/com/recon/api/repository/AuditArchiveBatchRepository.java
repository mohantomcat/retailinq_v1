package com.recon.api.repository;

import com.recon.api.domain.AuditArchiveBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditArchiveBatchRepository extends JpaRepository<AuditArchiveBatch, UUID> {
    List<AuditArchiveBatch> findTop20ByTenantIdOrderByCreatedAtDesc(String tenantId);

    Optional<AuditArchiveBatch> findTopByTenantIdOrderByCreatedAtDesc(String tenantId);
}
