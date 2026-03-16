package com.recon.api.repository;

import com.recon.api.domain.AlertEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertEventRepository extends JpaRepository<AlertEvent, UUID> {
    List<AlertEvent> findTop100ByTenantIdOrderByLastTriggeredAtDesc(String tenantId);

    List<AlertEvent> findTop100ByTenantIdAndReconViewOrderByLastTriggeredAtDesc(String tenantId, String reconView);

    long countByTenantIdAndAlertStatus(String tenantId, String alertStatus);

    long countByTenantIdAndAlertStatusAndSeverity(String tenantId, String alertStatus, String severity);

    @Query("""
            select e from AlertEvent e
            where e.ruleId = :ruleId
              and e.scopeKey = :scopeKey
              and e.alertStatus in ('OPEN', 'ACKNOWLEDGED')
            order by e.lastTriggeredAt desc
            """)
    List<AlertEvent> findActiveByRuleIdAndScopeKey(UUID ruleId, String scopeKey);

    default Optional<AlertEvent> findLatestActiveByRuleIdAndScopeKey(UUID ruleId, String scopeKey) {
        List<AlertEvent> events = findActiveByRuleIdAndScopeKey(ruleId, scopeKey);
        return events.isEmpty() ? Optional.empty() : Optional.of(events.get(0));
    }
}
