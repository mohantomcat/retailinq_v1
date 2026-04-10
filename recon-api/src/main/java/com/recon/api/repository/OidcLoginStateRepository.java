package com.recon.api.repository;

import com.recon.api.domain.OidcLoginState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OidcLoginStateRepository extends JpaRepository<OidcLoginState, String> {
    Optional<OidcLoginState> findByStateHashAndConsumedAtIsNull(String stateHash);

    void deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
