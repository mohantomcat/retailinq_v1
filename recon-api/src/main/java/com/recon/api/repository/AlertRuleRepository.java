package com.recon.api.repository;

import com.recon.api.domain.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {
    List<AlertRule> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    List<AlertRule> findByTenantIdAndReconViewOrderByUpdatedAtDesc(String tenantId, String reconView);

    List<AlertRule> findByActiveTrueOrderByUpdatedAtDesc();

    long countByTenantIdAndActiveTrue(String tenantId);
}
