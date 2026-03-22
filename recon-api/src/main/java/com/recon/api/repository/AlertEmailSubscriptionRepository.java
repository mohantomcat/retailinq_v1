package com.recon.api.repository;

import com.recon.api.domain.AlertEmailSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertEmailSubscriptionRepository extends JpaRepository<AlertEmailSubscription, UUID> {
    List<AlertEmailSubscription> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    List<AlertEmailSubscription> findByTenantIdAndReconViewOrderByUpdatedAtDesc(String tenantId, String reconView);

    List<AlertEmailSubscription> findByTenantIdAndReconViewAndActiveTrueOrderByUpdatedAtDesc(String tenantId, String reconView);

    long countByTenantIdAndActiveTrue(String tenantId);
}
