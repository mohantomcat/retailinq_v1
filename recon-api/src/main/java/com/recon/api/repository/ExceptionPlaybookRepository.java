package com.recon.api.repository;

import com.recon.api.domain.ExceptionPlaybook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExceptionPlaybookRepository extends JpaRepository<ExceptionPlaybook, UUID> {

    Optional<ExceptionPlaybook> findByIdAndTenantId(UUID id, String tenantId);

    @Query("""
            select distinct p from ExceptionPlaybook p
            left join fetch p.steps s
            where p.tenantId = :tenantId
              and (:reconView is null or p.reconView = :reconView)
            order by p.active desc, p.updatedAt desc
            """)
    List<ExceptionPlaybook> findForAutomationCenter(String tenantId, String reconView);

    @Query("""
            select distinct p from ExceptionPlaybook p
            left join fetch p.steps s
            where p.tenantId = :tenantId
              and p.reconView = :reconView
              and p.active = true
            order by p.updatedAt desc
            """)
    List<ExceptionPlaybook> findActivePlaybooks(String tenantId, String reconView);
}
