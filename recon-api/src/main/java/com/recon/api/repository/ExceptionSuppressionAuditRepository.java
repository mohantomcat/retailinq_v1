package com.recon.api.repository;

import com.recon.api.domain.ExceptionSuppressionAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ExceptionSuppressionAuditRepository extends JpaRepository<ExceptionSuppressionAudit, UUID> {

    List<ExceptionSuppressionAudit> findTop25ByTenantIdOrderByCreatedAtDesc(String tenantId);

    @Query("""
            select a from ExceptionSuppressionAudit a
            where a.tenantId = :tenantId
              and a.createdAt >= :since
            order by a.createdAt desc
            """)
    List<ExceptionSuppressionAudit> findRecentByTenantId(String tenantId, LocalDateTime since);
}
