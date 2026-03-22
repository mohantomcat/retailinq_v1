package com.recon.api.repository;

import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.ExceptionOutboundCommunication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ExceptionOutboundCommunicationRepository extends JpaRepository<ExceptionOutboundCommunication, UUID> {
    List<ExceptionOutboundCommunication> findTop50ByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<ExceptionOutboundCommunication> findByExceptionCaseOrderByCreatedAtDesc(ExceptionCase exceptionCase);

    List<ExceptionOutboundCommunication> findByTenantIdAndIncidentKeyInOrderByCreatedAtDesc(String tenantId, Collection<String> incidentKeys);
}
