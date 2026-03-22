package com.recon.api.repository;

import com.recon.api.domain.AlertSmsDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertSmsDeliveryRepository extends JpaRepository<AlertSmsDelivery, UUID> {
    List<AlertSmsDelivery> findTop100ByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<AlertSmsDelivery> findTop100ByTenantIdAndReconViewOrderByCreatedAtDesc(String tenantId, String reconView);
}
