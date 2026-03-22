package com.recon.api.repository;

import com.recon.api.domain.AlertWebhookSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertWebhookSubscriptionRepository extends JpaRepository<AlertWebhookSubscription, UUID> {
    List<AlertWebhookSubscription> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    List<AlertWebhookSubscription> findByTenantIdAndReconViewOrderByUpdatedAtDesc(String tenantId, String reconView);

    List<AlertWebhookSubscription> findByTenantIdAndReconViewAndActiveTrueOrderByUpdatedAtDesc(String tenantId, String reconView);

    long countByTenantIdAndActiveTrue(String tenantId);
}
