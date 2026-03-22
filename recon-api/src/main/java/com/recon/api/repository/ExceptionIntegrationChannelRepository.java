package com.recon.api.repository;

import com.recon.api.domain.ExceptionIntegrationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExceptionIntegrationChannelRepository extends JpaRepository<ExceptionIntegrationChannel, UUID> {
    Optional<ExceptionIntegrationChannel> findByIdAndTenantId(UUID id, String tenantId);

    List<ExceptionIntegrationChannel> findByTenantIdOrderByActiveDescUpdatedAtDesc(String tenantId);

    @Query("""
            select c from ExceptionIntegrationChannel c
            where c.tenantId = :tenantId
              and c.active = true
              and (:reconView is null or c.reconView is null or c.reconView = :reconView)
              and (:channelGroup is null or c.channelGroup = 'BOTH' or c.channelGroup = :channelGroup)
            order by c.updatedAt desc
            """)
    List<ExceptionIntegrationChannel> findActiveChannels(String tenantId,
                                                         String reconView,
                                                         String channelGroup);
}
