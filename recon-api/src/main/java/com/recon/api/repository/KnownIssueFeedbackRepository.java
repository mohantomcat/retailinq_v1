package com.recon.api.repository;

import com.recon.api.domain.KnownIssueFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnownIssueFeedbackRepository extends JpaRepository<KnownIssueFeedback, UUID> {
    Optional<KnownIssueFeedback> findByTenantIdAndKnownIssue_IdAndCreatedByAndContextKey(
            String tenantId,
            UUID knownIssueId,
            String createdBy,
            String contextKey
    );

    List<KnownIssueFeedback> findByTenantIdAndKnownIssue_IdIn(String tenantId, Collection<UUID> knownIssueIds);

    long countByTenantIdAndKnownIssue_IdAndHelpful(String tenantId, UUID knownIssueId, boolean helpful);
}
