package com.recon.api.repository;

import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.ExceptionCaseAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExceptionCaseAuditEventRepository extends JpaRepository<ExceptionCaseAuditEvent, UUID> {
    List<ExceptionCaseAuditEvent> findByExceptionCaseOrderByCreatedAtAsc(ExceptionCase exceptionCase);
}
