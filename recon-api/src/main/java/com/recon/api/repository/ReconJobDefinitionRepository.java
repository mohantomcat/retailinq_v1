package com.recon.api.repository;

import com.recon.api.domain.ReconJobDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ReconJobDefinitionRepository extends JpaRepository<ReconJobDefinition, UUID> {
    List<ReconJobDefinition> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<ReconJobDefinition> findByEnabledTrueAndNextScheduledAtLessThanEqualOrderByNextScheduledAtAsc(LocalDateTime dueAt);
}
