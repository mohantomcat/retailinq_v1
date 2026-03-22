package com.recon.api.repository;

import com.recon.api.domain.AuditLedgerArchiveEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLedgerArchiveEntryRepository extends JpaRepository<AuditLedgerArchiveEntry, UUID> {
    boolean existsByOriginalEntryId(UUID originalEntryId);

    long countByTenantId(String tenantId);
}
