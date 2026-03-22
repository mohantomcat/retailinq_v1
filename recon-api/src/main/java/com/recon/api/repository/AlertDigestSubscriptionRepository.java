package com.recon.api.repository;

import com.recon.api.domain.AlertDigestSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertDigestSubscriptionRepository extends JpaRepository<AlertDigestSubscription, UUID> {
    List<AlertDigestSubscription> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    List<AlertDigestSubscription> findByTenantIdAndReconViewOrderByUpdatedAtDesc(String tenantId, String reconView);

    List<AlertDigestSubscription> findByTenantIdAndActiveTrueOrderByUpdatedAtDesc(String tenantId);

    List<AlertDigestSubscription> findByActiveTrueOrderByUpdatedAtDesc();
}
