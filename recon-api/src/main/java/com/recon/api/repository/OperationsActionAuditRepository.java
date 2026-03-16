package com.recon.api.repository;

import com.recon.api.domain.OperationsActionAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OperationsActionAuditRepository extends JpaRepository<OperationsActionAudit, UUID> {
}
