package com.recon.api.repository;

import com.recon.api.domain.ReconJobNotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReconJobNotificationDeliveryRepository extends JpaRepository<ReconJobNotificationDelivery, UUID> {
    List<ReconJobNotificationDelivery> findByJobRunIdInOrderByCreatedAtDesc(Collection<UUID> jobRunIds);
}
