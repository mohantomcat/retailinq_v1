package com.recon.api.repository;

import com.recon.api.domain.ExceptionClosurePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExceptionClosurePolicyRepository extends JpaRepository<ExceptionClosurePolicy, UUID> {

    Optional<ExceptionClosurePolicy> findByIdAndTenantId(UUID id, String tenantId);

    @Query("""
            select p from ExceptionClosurePolicy p
            where p.tenantId = :tenantId
              and (:reconView is null or p.reconView = :reconView)
              and (:targetStatus is null or p.targetStatus = :targetStatus)
              and p.active = true
            order by p.reconView asc, p.targetStatus asc, p.updatedAt desc
            """)
    List<ExceptionClosurePolicy> findActivePolicies(String tenantId,
                                                    String reconView,
                                                    String targetStatus);

    @Query("""
            select p from ExceptionClosurePolicy p
            where p.tenantId = :tenantId
              and (:reconView is null or p.reconView = :reconView)
            order by p.active desc, p.reconView asc, p.targetStatus asc, p.updatedAt desc
            """)
    List<ExceptionClosurePolicy> findForApprovalCenter(String tenantId, String reconView);
}
