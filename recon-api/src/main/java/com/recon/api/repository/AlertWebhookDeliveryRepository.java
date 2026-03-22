package com.recon.api.repository;

import com.recon.api.domain.AlertWebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertWebhookDeliveryRepository extends JpaRepository<AlertWebhookDelivery, UUID> {
    List<AlertWebhookDelivery> findTop100ByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<AlertWebhookDelivery> findTop100ByTenantIdAndReconViewOrderByCreatedAtDesc(String tenantId, String reconView);

    long countByTenantIdAndDeliveryStatus(String tenantId, String deliveryStatus);
}
