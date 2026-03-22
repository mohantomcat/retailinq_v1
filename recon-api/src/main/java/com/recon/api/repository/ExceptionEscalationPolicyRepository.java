package com.recon.api.repository;

import com.recon.api.domain.ExceptionEscalationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExceptionEscalationPolicyRepository extends JpaRepository<ExceptionEscalationPolicy, UUID> {
    Optional<ExceptionEscalationPolicy> findByIdAndTenantId(UUID id, String tenantId);

    @Query("""
            select p from ExceptionEscalationPolicy p
            where p.tenantId = :tenantId
              and (:reconView is null or p.reconView = :reconView)
            order by p.active desc, p.reconView asc, p.updatedAt desc
            """)
    List<ExceptionEscalationPolicy> findForCenter(String tenantId, String reconView);

    @Query("""
            select p from ExceptionEscalationPolicy p
            where p.tenantId = :tenantId
              and p.reconView = :reconView
              and p.active = true
            order by p.updatedAt desc
            """)
    List<ExceptionEscalationPolicy> findActivePolicies(String tenantId, String reconView);
}
