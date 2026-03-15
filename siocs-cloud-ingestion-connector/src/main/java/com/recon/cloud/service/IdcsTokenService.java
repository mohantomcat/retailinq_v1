package com.recon.cloud.service;

import com.recon.cloud.config.CloudConnectorProperties;
import com.recon.cloud.domain.IdcsAccessTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class IdcsTokenService implements BearerTokenProvider {

    private final RestClient restClient;
    private final RetryTemplate cloudRestRetryTemplate;
    private final CloudConnectorProperties properties;

    private volatile CachedToken cachedToken;

    @Override
    public synchronized String getBearerToken() {
        if (isTokenUsable(cachedToken)) {
            return cachedToken.accessToken();
        }

        IdcsAccessTokenResponse response = cloudRestRetryTemplate.execute(context ->
                restClient.post()
                        .uri(properties.getIdcsTokenUrl())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .accept(MediaType.APPLICATION_JSON)
                        .headers(headers -> headers.setBasicAuth(
                                properties.getIdcsClientId(),
                                properties.getIdcsClientSecret()))
                        .body(buildTokenRequest())
                        .retrieve()
                        .body(IdcsAccessTokenResponse.class));

        if (response == null || response.getAccessToken() == null || response.getAccessToken().isBlank()) {
            throw new IllegalStateException("IDCS token response missing access_token");
        }

        long expiresIn = Objects.requireNonNullElse(response.getExpiresIn(), 300L);
        Instant expiresAt = Instant.now().plusSeconds(expiresIn);
        cachedToken = new CachedToken(response.getAccessToken(), expiresAt);
        log.debug("Refreshed IDCS access token expiring at {}", expiresAt);
        return cachedToken.accessToken();
    }

    private boolean isTokenUsable(CachedToken token) {
        if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
            return false;
        }
        return Instant.now().isBefore(
                token.expiresAt().minusSeconds(properties.getIdcsRefreshSkewSeconds()));
    }

    private MultiValueMap<String, String> buildTokenRequest() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", properties.getIdcsGrantType());
        if (properties.getIdcsScope() != null && !properties.getIdcsScope().isBlank()) {
            form.add("scope", properties.getIdcsScope());
        }
        return form;
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }
}
