package com.recon.api.repository;

import com.recon.api.domain.ExceptionSuppressionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExceptionSuppressionRuleRepository extends JpaRepository<ExceptionSuppressionRule, UUID> {

    Optional<ExceptionSuppressionRule> findByIdAndTenantId(UUID id, String tenantId);

    @Query("""
            select r from ExceptionSuppressionRule r
            where r.tenantId = :tenantId
              and (:reconView is null or r.reconView = :reconView)
            order by r.active desc, r.updatedAt desc
            """)
    List<ExceptionSuppressionRule> findForAutomationCenter(String tenantId, String reconView);

    @Query("""
            select r from ExceptionSuppressionRule r
            where r.tenantId = :tenantId
              and r.reconView = :reconView
              and r.active = true
            order by r.updatedAt desc
            """)
    List<ExceptionSuppressionRule> findActiveRules(String tenantId, String reconView);
}
