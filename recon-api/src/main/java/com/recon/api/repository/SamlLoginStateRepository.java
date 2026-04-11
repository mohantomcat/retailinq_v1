package com.recon.api.repository;

import com.recon.api.domain.SamlLoginState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SamlLoginStateRepository extends JpaRepository<SamlLoginState, String> {

    Optional<SamlLoginState> findByRelayStateHashAndConsumedAtIsNull(String relayStateHash);

    void deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
