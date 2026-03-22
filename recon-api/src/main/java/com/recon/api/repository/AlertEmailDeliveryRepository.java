package com.recon.api.repository;

import com.recon.api.domain.AlertEmailDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertEmailDeliveryRepository extends JpaRepository<AlertEmailDelivery, UUID> {
    List<AlertEmailDelivery> findTop100ByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<AlertEmailDelivery> findTop100ByTenantIdAndReconViewOrderByCreatedAtDesc(String tenantId, String reconView);

    long countByTenantIdAndDeliveryStatus(String tenantId, String deliveryStatus);
}
