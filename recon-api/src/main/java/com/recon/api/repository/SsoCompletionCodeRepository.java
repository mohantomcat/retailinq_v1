package com.recon.api.repository;

import com.recon.api.domain.SsoCompletionCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SsoCompletionCodeRepository extends JpaRepository<SsoCompletionCode, String> {

    Optional<SsoCompletionCode> findByCodeHashAndConsumedAtIsNull(String codeHash);

    void deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
