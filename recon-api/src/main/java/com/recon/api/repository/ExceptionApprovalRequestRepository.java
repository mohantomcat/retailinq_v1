package com.recon.api.repository;

import com.recon.api.domain.ExceptionApprovalRequest;
import com.recon.api.domain.ExceptionCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExceptionApprovalRequestRepository extends JpaRepository<ExceptionApprovalRequest, UUID> {

    Optional<ExceptionApprovalRequest> findByIdAndTenantId(UUID id, String tenantId);

    Optional<ExceptionApprovalRequest> findTopByExceptionCaseAndRequestStatusOrderByRequestedAtDesc(
            ExceptionCase exceptionCase,
            String requestStatus
    );

    List<ExceptionApprovalRequest> findByExceptionCaseOrderByRequestedAtAsc(ExceptionCase exceptionCase);

    @Query("""
            select r from ExceptionApprovalRequest r
            where r.tenantId = :tenantId
              and (:reconView is null or r.reconView = :reconView)
              and r.requestedAt >= :since
            order by r.requestedAt desc
            """)
    List<ExceptionApprovalRequest> findRecentRequests(String tenantId,
                                                      String reconView,
                                                      LocalDateTime since);
}
