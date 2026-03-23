package com.recon.api.repository;

import com.recon.api.domain.ReconJobRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReconJobRunRepository extends JpaRepository<ReconJobRun, UUID> {
    List<ReconJobRun> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    long countByTenantIdAndRunStatusAndCreatedAtAfter(String tenantId, String runStatus, LocalDateTime createdAt);

    long countByTenantIdAndRetryPendingTrue(String tenantId);

    boolean existsByJobDefinitionIdAndRunStatusIn(UUID jobDefinitionId, Collection<String> statuses);
}
