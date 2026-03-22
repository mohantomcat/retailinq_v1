package com.recon.api.repository;

import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.ExceptionExternalTicket;
import com.recon.api.domain.ExceptionExternalTicketSyncEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExceptionExternalTicketSyncEventRepository extends JpaRepository<ExceptionExternalTicketSyncEvent, UUID> {
    List<ExceptionExternalTicketSyncEvent> findByExceptionCaseOrderBySyncedAtDesc(ExceptionCase exceptionCase);

    List<ExceptionExternalTicketSyncEvent> findByTicketOrderBySyncedAtDesc(ExceptionExternalTicket ticket);
}
