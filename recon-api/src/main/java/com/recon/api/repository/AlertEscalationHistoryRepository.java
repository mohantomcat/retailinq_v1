package com.recon.api.repository;

import com.recon.api.domain.AlertEscalationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertEscalationHistoryRepository extends JpaRepository<AlertEscalationHistory, UUID> {
    List<AlertEscalationHistory> findTop100ByTenantIdOrderByEscalatedAtDesc(String tenantId);

    List<AlertEscalationHistory> findTop100ByTenantIdAndReconViewOrderByEscalatedAtDesc(String tenantId, String reconView);

    Optional<AlertEscalationHistory> findByPolicyIdAndEventId(UUID policyId, UUID eventId);

    long countByTenantId(String tenantId);
}
