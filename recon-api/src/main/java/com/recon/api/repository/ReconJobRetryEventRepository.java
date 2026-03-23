package com.recon.api.repository;

import com.recon.api.domain.ReconJobRetryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ReconJobRetryEventRepository extends JpaRepository<ReconJobRetryEvent, UUID> {
    List<ReconJobRetryEvent> findByRetryStatusAndScheduledForLessThanEqualOrderByScheduledForAsc(String retryStatus, LocalDateTime dueAt);
}
