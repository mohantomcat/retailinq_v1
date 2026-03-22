package com.recon.api.repository;

import com.recon.api.domain.OperationsActionAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OperationsActionAuditRepository extends JpaRepository<OperationsActionAudit, UUID> {
    List<OperationsActionAudit> findByTenantIdAndTransactionKeyAndReconViewOrderByCreatedAtDesc(String tenantId,
                                                                                                 String transactionKey,
                                                                                                 String reconView);
}
