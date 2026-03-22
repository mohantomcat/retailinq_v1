package com.recon.api.repository;

import com.recon.api.domain.ExceptionCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExceptionCaseRepository extends JpaRepository<ExceptionCase, UUID> {
    Optional<ExceptionCase> findByTenantIdAndTransactionKeyAndReconView(
            String tenantId,
            String transactionKey,
            String reconView
    );

    @Query("""
            select e from ExceptionCase e
            where e.tenantId = :tenantId
              and (:reconView is null or e.reconView = :reconView)
              and e.caseStatus not in ('RESOLVED', 'IGNORED')
              and e.updatedAt >= :since
            """)
    List<ExceptionCase> findActiveCasesForAging(String tenantId,
                                                String reconView,
                                                LocalDateTime since);

    @Query("""
            select e from ExceptionCase e
            where e.caseStatus not in ('RESOLVED', 'IGNORED')
              and e.updatedAt >= :since
            """)
    List<ExceptionCase> findRecentActiveCases(LocalDateTime since);

    @Query("""
            select e from ExceptionCase e
            where e.tenantId = :tenantId
              and (:reconView is null or e.reconView = :reconView)
              and (:storeId is null or e.storeId = :storeId)
              and e.createdAt >= :since
            """)
    List<ExceptionCase> findForRootCauseAnalytics(String tenantId,
                                                  String reconView,
                                                  String storeId,
                                                  LocalDateTime since);

    @Query("""
            select e from ExceptionCase e
            where e.tenantId = :tenantId
              and (:reconView is null or e.reconView = :reconView)
              and e.createdAt >= :since
            """)
    List<ExceptionCase> findForRecurrenceAnalytics(String tenantId,
                                                   String reconView,
                                                   LocalDateTime since);
}
