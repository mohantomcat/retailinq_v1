package com.recon.xocs.service;

import com.recon.xocs.config.XocsConnectorProperties;
import com.recon.xocs.domain.XocsApiPage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class XocsRestClient {

    private static final DateTimeFormatter ORDS_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .withZone(ZoneOffset.UTC);

    private final RestClient restClient;
    private final RetryTemplate xocsRetryTemplate;
    private final XocsConnectorProperties properties;
    private final StaticBearerTokenProvider staticBearerTokenProvider;
    private final IdcsTokenService idcsTokenService;

    public XocsApiPage fetchTransactions(LocalDate businessDate,
                                         Long lastCursorId,
                                         Timestamp fromTimestamp,
                                         int limit) {
        String from = ORDS_TIMESTAMP_FORMAT.format(
                fromTimestamp == null ? Instant.EPOCH : fromTimestamp.toInstant());

        Map<String, Object> response = xocsRetryTemplate.execute(context ->
                restClient.get()
                        .uri(properties.getBaseUrl() + properties.getTransactionsPath(), uriBuilder ->
                                uriBuilder
                                        .queryParam("organizationId", properties.getOrgId())
                                        .queryParam("businessDate", businessDate)
                                        .queryParam("fromTimestamp", from)
                                        .queryParam("lastCursorId", lastCursorId == null ? 0L : lastCursorId)
                                        .queryParam("limit", limit)
                                        .build())
                        .accept(MediaType.APPLICATION_JSON)
                        .headers(this::applyHeaders)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {
                        }));

        return response == null
                ? XocsApiPage.builder().build()
                : XocsJsonMappingSupport.mapPage(response);
    }

    private void applyHeaders(HttpHeaders headers) {
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        String authType = Objects.toString(properties.getAuthType(), "NONE").trim().toUpperCase();
        switch (authType) {
            case "NONE" -> {
                if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
                    headers.set(Objects.toString(properties.getAuthHeaderName(), "X-API-Key"),
                            properties.getApiKey());
                }
            }
            case "API_KEY" -> headers.set(
                    Objects.toString(properties.getAuthHeaderName(), "X-API-Key"),
                    properties.getApiKey());
            case "BEARER" -> headers.setBearerAuth(staticBearerTokenProvider.getBearerToken());
            case "IDCS" -> headers.setBearerAuth(idcsTokenService.getBearerToken());
            case "BASIC" -> headers.set(HttpHeaders.AUTHORIZATION, basicAuthValue());
            default -> throw new IllegalArgumentException("Unsupported authType: " + authType);
        }
    }

    private String basicAuthValue() {
        String raw = Objects.toString(properties.getBasicUsername(), "") +
                ":" + Objects.toString(properties.getBasicPassword(), "");
        return "Basic " + Base64.getEncoder()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
