package com.recon.api.repository;

import com.recon.api.domain.AlertDigestRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertDigestRunRepository extends JpaRepository<AlertDigestRun, UUID> {
    List<AlertDigestRun> findTop100ByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<AlertDigestRun> findTop100ByTenantIdAndReconViewOrderByCreatedAtDesc(String tenantId, String reconView);
}
