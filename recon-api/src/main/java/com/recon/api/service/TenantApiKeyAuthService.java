package com.recon.api.service;

import com.recon.api.domain.TenantApiKey;
import com.recon.api.domain.TenantAuthConfigEntity;
import com.recon.api.repository.TenantApiKeyRepository;
import com.recon.api.repository.TenantAuthConfigRepository;
import com.recon.api.security.ReconUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class TenantApiKeyAuthService {

    private final TenantApiKeyRepository tenantApiKeyRepository;
    private final TenantAuthConfigRepository tenantAuthConfigRepository;

    public TenantApiKeyAuthService(TenantApiKeyRepository tenantApiKeyRepository,
                                   TenantAuthConfigRepository tenantAuthConfigRepository) {
        this.tenantApiKeyRepository = tenantApiKeyRepository;
        this.tenantAuthConfigRepository = tenantAuthConfigRepository;
    }

    @Transactional
    public ReconUserPrincipal authenticate(String apiKeyValue) {
        String normalizedValue = trimToNull(apiKeyValue);
        if (normalizedValue == null || !normalizedValue.contains(".")) {
            return null;
        }

        String prefix = normalizedValue.substring(0, normalizedValue.indexOf('.'));
        TenantApiKey apiKey = tenantApiKeyRepository.findByKeyPrefixAndActiveTrue(prefix).orElse(null);
        if (apiKey == null || !hashesMatch(apiKey.getKeyHash(), hashKey(normalizedValue))) {
            return null;
        }

        if (apiKey.getRevokedAt() != null
                || (apiKey.getExpiresAt() != null
                && apiKey.getExpiresAt().isBefore(LocalDateTime.now()))) {
            return null;
        }

        TenantAuthConfigEntity authConfig = tenantAuthConfigRepository.findById(apiKey.getTenantId()).orElse(null);
        if (authConfig != null && !authConfig.isApiKeyAuthEnabled()) {
            return null;
        }

        apiKey.setLastUsedAt(LocalDateTime.now());
        apiKey.setLastUsedBy(apiKey.getKeyName());
        tenantApiKeyRepository.save(apiKey);

        return new ReconUserPrincipal(
                "api-key:" + apiKey.getId(),
                apiKey.getKeyName(),
                apiKey.getTenantId(),
                parseCsv(apiKey.getPermissionCodes()),
                parseCsv(apiKey.getAllowedStoreIds()),
                apiKey.isAllStoreAccess(),
                "API_KEY"
        );
    }

    public static String hashKey(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte current : hash) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash API key", ex);
        }
    }

    private boolean hashesMatch(String expectedHash, String actualHash) {
        if (expectedHash == null || actualHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                actualHash.getBytes(StandardCharsets.UTF_8));
    }

    private Set<String> parseCsv(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(trimmed.split(","))
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .map(entry -> entry.toUpperCase(Locale.ROOT))
                .forEach(result::add);
        return result;
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
