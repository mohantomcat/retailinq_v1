package com.recon.api.service;

import com.recon.api.domain.AuditLedgerWriteRequest;
import com.recon.api.domain.LoginResponse;
import com.recon.api.domain.OidcLoginCallbackRequest;
import com.recon.api.domain.OidcLoginStartRequest;
import com.recon.api.domain.OidcLoginStartResponse;
import com.recon.api.domain.OidcLoginState;
import com.recon.api.domain.TenantAuthConfigEntity;
import com.recon.api.domain.User;
import com.recon.api.repository.OidcLoginStateRepository;
import com.recon.api.repository.TenantAuthConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OidcLoginService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final int LOGIN_STATE_TTL_MINUTES = 10;

    private final TenantAuthConfigRepository tenantAuthConfigRepository;
    private final OidcLoginStateRepository oidcLoginStateRepository;
    private final RestTemplateBuilder restTemplateBuilder;
    private final AuthService authService;
    private final EnterpriseIdentityLifecycleService enterpriseIdentityLifecycleService;
    private final AuditLedgerService auditLedgerService;

    @Transactional
    public OidcLoginStartResponse startLogin(OidcLoginStartRequest request) {
        String tenantId = trimToNull(request != null ? request.getTenantId() : null);
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant is required");
        }
        TenantAuthConfigEntity config = resolveEnabledConfig(tenantId);
        OidcProviderMetadata metadata = discoverProvider(config);

        oidcLoginStateRepository.deleteByExpiresAtBefore(LocalDateTime.now().minusMinutes(1));

        String state = randomUrlToken(32);
        String nonce = randomUrlToken(32);
        String verifier = randomUrlToken(64);
        String challenge = sha256Url(verifier);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(LOGIN_STATE_TTL_MINUTES);

        oidcLoginStateRepository.save(OidcLoginState.builder()
                .stateHash(sha256Hex(state))
                .tenantId(tenantId)
                .redirectUri(config.getOidcRedirectUri())
                .codeVerifier(verifier)
                .nonce(nonce)
                .expiresAt(expiresAt)
                .build());

        String authorizationUrl = UriComponentsBuilder
                .fromUriString(metadata.authorizationEndpoint())
                .queryParam("response_type", "code")
                .queryParam("client_id", config.getOidcClientId())
                .queryParam("redirect_uri", config.getOidcRedirectUri())
                .queryParam("scope", defaultIfBlank(config.getOidcScopes(), "openid profile email"))
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUriString();

        return OidcLoginStartResponse.builder()
                .authorizationUrl(authorizationUrl)
                .state(state)
                .expiresAt(expiresAt)
                .build();
    }

    @Transactional
    public LoginResponse completeLogin(OidcLoginCallbackRequest request) {
        String state = trimToNull(request != null ? request.getState() : null);
        String code = trimToNull(request != null ? request.getCode() : null);
        if (trimToNull(request != null ? request.getError() : null) != null) {
            throw new IllegalArgumentException("OIDC provider rejected login: "
                    + defaultIfBlank(request.getErrorDescription(), request.getError()));
        }
        if (state == null || code == null) {
            throw new IllegalArgumentException("OIDC code and state are required");
        }

        OidcLoginState loginState = oidcLoginStateRepository
                .findByStateHashAndConsumedAtIsNull(sha256Hex(state))
                .orElseThrow(() -> new IllegalArgumentException("OIDC login state is invalid or already used"));
        if (loginState.getExpiresAt() == null || loginState.getExpiresAt().isBefore(LocalDateTime.now())) {
            loginState.setConsumedAt(LocalDateTime.now());
            oidcLoginStateRepository.save(loginState);
            throw new IllegalArgumentException("OIDC login state expired");
        }
        loginState.setConsumedAt(LocalDateTime.now());
        oidcLoginStateRepository.save(loginState);

        TenantAuthConfigEntity config = resolveEnabledConfig(loginState.getTenantId());
        if (!Objects.equals(trimToNull(config.getOidcRedirectUri()), trimToNull(loginState.getRedirectUri()))) {
            throw new IllegalArgumentException("OIDC redirect URI no longer matches tenant configuration");
        }

        OidcProviderMetadata metadata = discoverProvider(config);
        Map<String, Object> tokenResponse = exchangeCode(config, metadata, code, loginState);
        String idToken = asString(tokenResponse.get("id_token"));
        if (idToken == null) {
            throw new IllegalArgumentException("OIDC token response did not include an ID token");
        }

        Jwt jwt = validateIdToken(config, metadata, idToken, loginState.getNonce());
        OidcUserClaims claims = extractClaims(config, jwt);
        User user = enterpriseIdentityLifecycleService.syncSsoIdentity(
                config,
                "OIDC",
                new EnterpriseIdentityLifecycleService.ExternalIdentityProfile(
                        claims.subject(),
                        claims.username(),
                        claims.email(),
                        claims.fullName(),
                        claims.emailVerified(),
                        claims.groups()),
                "oidc");
        recordSecurityAudit(config.getTenantId(),
                "OIDC_LOGIN_ACCEPTED",
                "OIDC login accepted",
                user.getId().toString(),
                user.getUsername(),
                Map.of("issuer", metadata.issuer(), "subject", claims.subject()));
        return authService.completeExternalLogin(user.getId(), "OIDC");
    }

    private TenantAuthConfigEntity resolveEnabledConfig(String tenantId) {
        TenantAuthConfigEntity config = tenantAuthConfigRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant OIDC configuration was not found"));
        if (!config.isOidcEnabled()) {
            throw new IllegalArgumentException("OIDC login is not enabled for this tenant");
        }
        requireField(config.getOidcIssuerUrl(), "OIDC issuer URL");
        requireField(config.getOidcClientId(), "OIDC client id");
        requireField(config.getOidcRedirectUri(), "OIDC redirect URI");
        return config;
    }

    @SuppressWarnings("unchecked")
    private OidcProviderMetadata discoverProvider(TenantAuthConfigEntity config) {
        String issuer = normalizeIssuer(config.getOidcIssuerUrl());
        String discoveryUrl = issuer + "/.well-known/openid-configuration";
        try {
            Map<String, Object> response = restTemplate().getForObject(discoveryUrl, Map.class);
            if (response == null) {
                throw new IllegalArgumentException("OIDC discovery response was empty");
            }
            String metadataIssuer = defaultIfBlank(asString(response.get("issuer")), issuer);
            String authorizationEndpoint = asString(response.get("authorization_endpoint"));
            String tokenEndpoint = asString(response.get("token_endpoint"));
            String jwksUri = asString(response.get("jwks_uri"));
            requireField(authorizationEndpoint, "OIDC authorization endpoint");
            requireField(tokenEndpoint, "OIDC token endpoint");
            requireField(jwksUri, "OIDC JWKS URI");
            return new OidcProviderMetadata(metadataIssuer, authorizationEndpoint, tokenEndpoint, jwksUri);
        } catch (RestClientException ex) {
            throw new IllegalArgumentException("OIDC provider discovery failed: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchangeCode(TenantAuthConfigEntity config,
                                             OidcProviderMetadata metadata,
                                             String code,
                                             OidcLoginState loginState) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", loginState.getRedirectUri());
        body.add("client_id", config.getOidcClientId());
        body.add("code_verifier", loginState.getCodeVerifier());
        String clientSecret = resolveClientSecret(config);
        if (clientSecret != null) {
            body.add("client_secret", clientSecret);
        }

        try {
            ResponseEntity<Map> response = restTemplate().postForEntity(
                    metadata.tokenEndpoint(),
                    new HttpEntity<>(body, headers),
                    Map.class);
            Map<?, ?> responseBody = response.getBody();
            if (responseBody == null) {
                throw new IllegalArgumentException("OIDC token response was empty");
            }
            return responseBody.entrySet().stream()
                    .collect(Collectors.toMap(
                            entry -> Objects.toString(entry.getKey()),
                            Map.Entry::getValue,
                            (left, right) -> right,
                            LinkedHashMap::new));
        } catch (RestClientException ex) {
            throw new IllegalArgumentException("OIDC token exchange failed: " + ex.getMessage(), ex);
        }
    }

    private Jwt validateIdToken(TenantAuthConfigEntity config,
                                OidcProviderMetadata metadata,
                                String idToken,
                                String expectedNonce) {
        try {
            Jwt jwt = NimbusJwtDecoder.withJwkSetUri(metadata.jwksUri()).build().decode(idToken);
            String issuer = jwt.getIssuer() != null ? normalizeIssuer(jwt.getIssuer().toString()) : null;
            if (!Objects.equals(issuer, normalizeIssuer(metadata.issuer()))) {
                throw new IllegalArgumentException("OIDC issuer validation failed");
            }
            if (!jwt.getAudience().contains(config.getOidcClientId())) {
                throw new IllegalArgumentException("OIDC audience validation failed");
            }
            String nonce = claimAsString(jwt, "nonce");
            if (!Objects.equals(nonce, expectedNonce)) {
                throw new IllegalArgumentException("OIDC nonce validation failed");
            }
            return jwt;
        } catch (JwtException ex) {
            throw new IllegalArgumentException("OIDC ID token validation failed: " + ex.getMessage(), ex);
        }
    }

    private OidcUserClaims extractClaims(TenantAuthConfigEntity config,
                                        Jwt jwt) {
        String subject = defaultIfBlank(jwt.getSubject(), null);
        if (subject == null) {
            throw new IllegalArgumentException("OIDC subject claim is required");
        }
        String email = firstNonBlank(
                claimAsString(jwt, config.getOidcEmailClaim()),
                claimAsString(jwt, "email"),
                claimAsString(jwt, "preferred_username"),
                claimAsString(jwt, "upn"));
        String username = firstNonBlank(
                claimAsString(jwt, config.getOidcUsernameClaim()),
                claimAsString(jwt, "preferred_username"),
                claimAsString(jwt, "upn"),
                email);
        String fullName = firstNonBlank(
                claimAsString(jwt, "name"),
                buildName(claimAsString(jwt, "given_name"), claimAsString(jwt, "family_name")),
                username);
        Set<String> groups = claimAsStringSet(jwt, config.getOidcGroupsClaim());
        if (!"roles".equalsIgnoreCase(defaultIfBlank(config.getOidcGroupsClaim(), ""))) {
            groups.addAll(claimAsStringSet(jwt, "roles"));
        }
        return new OidcUserClaims(
                subject,
                username,
                email,
                fullName,
                Boolean.TRUE.equals(asBoolean(claimValue(jwt, "email_verified"))),
                groups);
    }

    private String resolveClientSecret(TenantAuthConfigEntity config) {
        String secretRef = trimToNull(config.getOidcClientSecretRef());
        if (secretRef == null) {
            return null;
        }
        String secret = trimToNull(System.getenv(secretRef));
        if (secret == null) {
            secret = trimToNull(System.getProperty(secretRef));
        }
        if (secret == null) {
            throw new IllegalArgumentException("OIDC client secret reference is configured but no value was found");
        }
        return secret;
    }

    private RestTemplate restTemplate() {
        return restTemplateBuilder
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setReadTimeout(READ_TIMEOUT)
                .build();
    }

    private Object claimValue(Jwt jwt, String claimPath) {
        String path = trimToNull(claimPath);
        if (path == null) {
            return null;
        }
        Object current = jwt.getClaims();
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private String claimAsString(Jwt jwt, String claimPath) {
        return asString(claimValue(jwt, claimPath));
    }

    private Set<String> claimAsStringSet(Jwt jwt, String claimPath) {
        Object value = claimValue(jwt, claimPath);
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(this::asString)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        String stringValue = asString(value);
        if (stringValue == null) {
            return new LinkedHashSet<>();
        }
        return java.util.Arrays.stream(stringValue.split(","))
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String buildName(String givenName, String familyName) {
        String combined = (defaultIfBlank(givenName, "") + " " + defaultIfBlank(familyName, "")).trim();
        return trimToNull(combined);
    }

    private String normalizeIssuer(String value) {
        String issuer = trimToNull(value);
        if (issuer == null) {
            return null;
        }
        while (issuer.endsWith("/")) {
            issuer = issuer.substring(0, issuer.length() - 1);
        }
        return issuer;
    }

    private String sha256Url(String value) {
        byte[] digest = sha256(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private String sha256Hex(String value) {
        byte[] digest = sha256(value);
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String randomUrlToken(int bytes) {
        byte[] token = new byte[bytes];
        RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return trimToNull(string);
        }
        return trimToNull(Objects.toString(value, null));
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String stringValue = asString(value);
        return stringValue == null ? null : Boolean.parseBoolean(stringValue);
    }

    private void requireField(String value, String label) {
        if (trimToNull(value) == null) {
            throw new IllegalArgumentException(label + " is required");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private void recordSecurityAudit(String tenantId,
                                     String actionType,
                                     String title,
                                     String entityKey,
                                     String actor,
                                     Object metadata) {
        auditLedgerService.record(AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("SECURITY")
                .moduleKey("SECURITY")
                .entityType("OIDC_IDENTITY")
                .entityKey(entityKey)
                .actionType(actionType)
                .title(title)
                .actor(defaultIfBlank(actor, "oidc"))
                .status(actionType)
                .referenceKey(entityKey)
                .controlFamily("ACCESS_CONTROL")
                .evidenceTags(List.of("SECURITY", "OIDC", "SSO"))
                .metadata(metadata)
                .build());
    }

    private record OidcProviderMetadata(
            String issuer,
            String authorizationEndpoint,
            String tokenEndpoint,
            String jwksUri) {
    }

    private record OidcUserClaims(
            String subject,
            String username,
            String email,
            String fullName,
            boolean emailVerified,
            Set<String> groups) {
    }
}
