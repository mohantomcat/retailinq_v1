package com.recon.api.repository;

import com.recon.api.domain.AlertUserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertUserSubscriptionRepository extends JpaRepository<AlertUserSubscription, UUID> {
    List<AlertUserSubscription> findByTenantIdAndUserIdOrderByUpdatedAtDesc(String tenantId, UUID userId);

    List<AlertUserSubscription> findByTenantIdAndReconViewAndActiveTrueOrderByUpdatedAtDesc(String tenantId, String reconView);

    long countByTenantIdAndUserIdAndActiveTrue(String tenantId, UUID userId);
}
