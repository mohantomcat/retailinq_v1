package com.recon.api.repository;

import com.recon.api.domain.AuditLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditLedgerEntryRepository extends JpaRepository<AuditLedgerEntry, UUID> {
    Optional<AuditLedgerEntry> findTopByTenantIdOrderByEntryNumberDesc(String tenantId);

    List<AuditLedgerEntry> findTop100ByTenantIdAndSourceTypeOrderByEventAtDesc(String tenantId, String sourceType);

    List<AuditLedgerEntry> findTop500ByTenantIdAndEventAtBeforeOrderByEventAtAsc(String tenantId, LocalDateTime eventAt);

    long countByTenantId(String tenantId);
}
