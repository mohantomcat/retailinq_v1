package com.recon.api.repository;

import com.recon.api.domain.AccessGovernanceNotificationJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AccessGovernanceNotificationJobRepository extends JpaRepository<AccessGovernanceNotificationJob, UUID> {

    List<AccessGovernanceNotificationJob> findTop100ByNotificationStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscCreatedAtAsc(
            Collection<String> statuses,
            LocalDateTime nextAttemptAt);

    List<AccessGovernanceNotificationJob> findTop100ByTenantIdAndNotificationStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscCreatedAtAsc(
            String tenantId,
            Collection<String> statuses,
            LocalDateTime nextAttemptAt);

    List<AccessGovernanceNotificationJob> findByTenantIdAndNotificationStatusInOrderByCreatedAtDesc(
            String tenantId,
            Collection<String> statuses);

    List<AccessGovernanceNotificationJob> findTop50ByTenantIdOrderByCreatedAtDesc(String tenantId);

    boolean existsByNotificationContextKeyAndNotificationStatusIn(
            String notificationContextKey,
            Collection<String> statuses);
}
