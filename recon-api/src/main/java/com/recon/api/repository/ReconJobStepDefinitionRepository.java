package com.recon.api.repository;

import com.recon.api.domain.ReconJobStepDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReconJobStepDefinitionRepository extends JpaRepository<ReconJobStepDefinition, UUID> {
    List<ReconJobStepDefinition> findByJobDefinitionIdOrderByStepOrderAsc(UUID jobDefinitionId);

    void deleteByJobDefinitionId(UUID jobDefinitionId);
}
