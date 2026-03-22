package com.recon.api.repository;

import com.recon.api.domain.ExceptionRoutingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExceptionRoutingRuleRepository extends JpaRepository<ExceptionRoutingRule, UUID> {

    Optional<ExceptionRoutingRule> findByIdAndTenantId(UUID id, String tenantId);

    @Query("""
            select r from ExceptionRoutingRule r
            where r.tenantId = :tenantId
              and (:reconView is null or r.reconView = :reconView)
            order by r.active desc, r.priority asc, r.updatedAt desc
            """)
    List<ExceptionRoutingRule> findForAutomationCenter(String tenantId, String reconView);

    @Query("""
            select r from ExceptionRoutingRule r
            where r.tenantId = :tenantId
              and r.reconView = :reconView
              and r.active = true
            order by r.priority asc, r.updatedAt desc
            """)
    List<ExceptionRoutingRule> findActiveRules(String tenantId, String reconView);
}
