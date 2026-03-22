package com.recon.api.repository;

import com.recon.api.domain.AlertSmsSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertSmsSubscriptionRepository extends JpaRepository<AlertSmsSubscription, UUID> {
    List<AlertSmsSubscription> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    List<AlertSmsSubscription> findByTenantIdAndReconViewOrderByUpdatedAtDesc(String tenantId, String reconView);

    List<AlertSmsSubscription> findByTenantIdAndReconViewAndActiveTrueOrderByUpdatedAtDesc(String tenantId, String reconView);
}
