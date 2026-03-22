package com.recon.api.repository;

import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.ExceptionExternalTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExceptionExternalTicketRepository extends JpaRepository<ExceptionExternalTicket, UUID> {
    List<ExceptionExternalTicket> findTop50ByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<ExceptionExternalTicket> findByExceptionCaseOrderByCreatedAtDesc(ExceptionCase exceptionCase);

    List<ExceptionExternalTicket> findByTenantIdAndIncidentKeyInOrderByCreatedAtDesc(String tenantId, Collection<String> incidentKeys);

    Optional<ExceptionExternalTicket> findTopByExceptionCaseAndChannelOrderByCreatedAtDesc(ExceptionCase exceptionCase,
                                                                                           com.recon.api.domain.ExceptionIntegrationChannel channel);

    Optional<ExceptionExternalTicket> findTopByTenantIdAndChannelIdAndExternalReferenceOrderByCreatedAtDesc(String tenantId,
                                                                                                             UUID channelId,
                                                                                                             String externalReference);

    Optional<ExceptionExternalTicket> findTopByTenantIdAndChannelIdAndTransactionKeyAndReconViewOrderByCreatedAtDesc(String tenantId,
                                                                                                                       UUID channelId,
                                                                                                                       String transactionKey,
                                                                                                                       String reconView);

    Optional<ExceptionExternalTicket> findTopByTenantIdAndChannelIdAndTransactionKeyOrderByCreatedAtDesc(String tenantId,
                                                                                                           UUID channelId,
                                                                                                           String transactionKey);

    Optional<ExceptionExternalTicket> findTopByTenantIdAndChannelIdAndIncidentKeyOrderByCreatedAtDesc(String tenantId,
                                                                                                        UUID channelId,
                                                                                                        String incidentKey);
}
