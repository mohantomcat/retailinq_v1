package com.recon.api.service;

import com.recon.api.domain.TenantAuthConfigEntity;
import com.recon.api.repository.TenantAuthConfigRepository;
import com.recon.api.security.ReconUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ScimAuthenticationService {

    private final TenantAuthConfigRepository tenantAuthConfigRepository;

    public ReconUserPrincipal authenticate(String tenantId,
                                           String bearerToken) {
        String normalizedTenantId = trimToNull(tenantId);
        String normalizedToken = trimToNull(bearerToken);
        if (normalizedTenantId == null) {
            throw new IllegalArgumentException("SCIM tenant id is required");
        }
        if (normalizedToken == null) {
            throw new IllegalArgumentException("SCIM bearer token is required");
        }

        TenantAuthConfigEntity config = tenantAuthConfigRepository.findById(normalizedTenantId)
                .orElseThrow(() -> new IllegalArgumentException("SCIM tenant configuration was not found"));
        if (!config.isScimEnabled()) {
            throw new IllegalArgumentException("SCIM provisioning is not enabled for this tenant");
        }

        String expectedToken = resolveSecret(config.getScimBearerTokenRef());
        if (!MessageDigest.isEqual(
                normalizedToken.getBytes(StandardCharsets.UTF_8),
                expectedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("SCIM bearer token is invalid");
        }

        return new ReconUserPrincipal(
                "scim:" + normalizedTenantId,
                "scim",
                normalizedTenantId,
                Set.of("SCIM_PROVISION"),
                Set.of(),
                true,
                "SCIM");
    }

    private String resolveSecret(String secretRef) {
        String normalizedRef = trimToNull(secretRef);
        if (normalizedRef == null) {
            throw new IllegalArgumentException("SCIM bearer token reference is not configured");
        }
        String secret = trimToNull(System.getenv(normalizedRef));
        if (secret == null) {
            secret = trimToNull(System.getProperty(normalizedRef));
        }
        if (secret == null) {
            throw new IllegalArgumentException("SCIM bearer token reference is configured but no value was found");
        }
        return secret;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
