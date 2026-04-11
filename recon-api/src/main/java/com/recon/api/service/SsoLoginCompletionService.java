package com.recon.api.service;

import com.recon.api.domain.LoginResponse;
import com.recon.api.domain.SsoCompletionCode;
import com.recon.api.domain.User;
import com.recon.api.repository.SsoCompletionCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class SsoLoginCompletionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SsoCompletionCodeRepository ssoCompletionCodeRepository;
    private final AuthService authService;

    @Transactional
    public String issueCode(User user, String authMode) {
        ssoCompletionCodeRepository.deleteByExpiresAtBefore(LocalDateTime.now().minusMinutes(1));

        String code = randomUrlToken(32);
        ssoCompletionCodeRepository.save(SsoCompletionCode.builder()
                .codeHash(sha256Hex(code))
                .tenantId(user.getTenantId())
                .user(user)
                .authMode(authMode)
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .build());
        return code;
    }

    @Transactional
    public LoginResponse exchange(String code) {
        String normalizedCode = trimToNull(code);
        if (normalizedCode == null) {
            throw new IllegalArgumentException("SSO completion code is required");
        }
        SsoCompletionCode completionCode = ssoCompletionCodeRepository
                .findByCodeHashAndConsumedAtIsNull(sha256Hex(normalizedCode))
                .orElseThrow(() -> new IllegalArgumentException("SSO completion code is invalid or already used"));
        if (completionCode.getExpiresAt() == null || completionCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            completionCode.setConsumedAt(LocalDateTime.now());
            ssoCompletionCodeRepository.save(completionCode);
            throw new IllegalArgumentException("SSO completion code expired");
        }
        completionCode.setConsumedAt(LocalDateTime.now());
        ssoCompletionCodeRepository.save(completionCode);
        return authService.completeExternalLogin(
                completionCode.getUser().getId(),
                completionCode.getAuthMode());
    }

    private String randomUrlToken(int bytes) {
        byte[] token = new byte[bytes];
        RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
