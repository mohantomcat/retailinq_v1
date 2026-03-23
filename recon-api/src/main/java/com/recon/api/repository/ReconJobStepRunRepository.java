package com.recon.api.repository;

import com.recon.api.domain.ReconJobStepRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReconJobStepRunRepository extends JpaRepository<ReconJobStepRun, UUID> {
    List<ReconJobStepRun> findByJobRunIdInOrderByJobRunIdAscStepOrderAsc(Collection<UUID> jobRunIds);
}
