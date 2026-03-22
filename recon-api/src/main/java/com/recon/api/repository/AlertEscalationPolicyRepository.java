package com.recon.api.repository;

import com.recon.api.domain.AlertEscalationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertEscalationPolicyRepository extends JpaRepository<AlertEscalationPolicy, UUID> {
    List<AlertEscalationPolicy> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    List<AlertEscalationPolicy> findByTenantIdAndReconViewOrderByUpdatedAtDesc(String tenantId, String reconView);

    List<AlertEscalationPolicy> findByActiveTrueOrderByUpdatedAtDesc();

    long countByTenantIdAndActiveTrue(String tenantId);
}
