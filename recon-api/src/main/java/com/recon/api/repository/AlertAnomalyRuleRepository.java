package com.recon.api.repository;

import com.recon.api.domain.AlertAnomalyRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertAnomalyRuleRepository extends JpaRepository<AlertAnomalyRule, UUID> {
    List<AlertAnomalyRule> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    List<AlertAnomalyRule> findByTenantIdAndReconViewOrderByUpdatedAtDesc(String tenantId, String reconView);

    List<AlertAnomalyRule> findByActiveTrueOrderByUpdatedAtDesc();
}
