package com.recon.api.repository;

import com.recon.api.domain.ExceptionSlaRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExceptionSlaRuleRepository extends JpaRepository<ExceptionSlaRule, UUID> {
    List<ExceptionSlaRule> findByTenantIdOrderByReconViewAscSeverityAsc(String tenantId);

    List<ExceptionSlaRule> findByTenantIdAndReconViewOrderBySeverityAsc(String tenantId, String reconView);

    Optional<ExceptionSlaRule> findByTenantIdAndReconViewAndSeverity(String tenantId,
                                                                     String reconView,
                                                                     String severity);
}
