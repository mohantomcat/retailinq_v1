package com.recon.api.repository;

import com.recon.api.domain.KnownIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnownIssueRepository extends JpaRepository<KnownIssue, UUID> {
    Optional<KnownIssue> findByIdAndTenantId(UUID id, String tenantId);

    List<KnownIssue> findByTenantIdOrderByActiveDescPriorityWeightDescUpdatedAtDesc(String tenantId);

    List<KnownIssue> findByTenantIdAndActiveTrueOrderByPriorityWeightDescUpdatedAtDesc(String tenantId);

    boolean existsByTenantIdAndIssueKeyIgnoreCaseAndIdNot(String tenantId, String issueKey, UUID id);

    boolean existsByTenantIdAndIssueKeyIgnoreCase(String tenantId, String issueKey);
}
