package com.recon.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.api.domain.AuditLedgerEntry;
import com.recon.api.domain.AuditLedgerWriteRequest;
import com.recon.api.repository.AuditLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuditLedgerService {

    private final AuditLedgerEntryRepository auditLedgerEntryRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void record(AuditLedgerWriteRequest request) {
        if (request == null || isBlank(request.getTenantId()) || isBlank(request.getTitle())) {
            return;
        }
        LocalDateTime eventAt = request.getEventAt() != null ? request.getEventAt() : LocalDateTime.now();
        String previousHash = auditLedgerEntryRepository.findTopByTenantIdOrderByEntryNumberDesc(request.getTenantId())
                .map(AuditLedgerEntry::getEventHash)
                .orElse(null);
        String beforeState = serialize(request.getBeforeState());
        String afterState = serialize(request.getAfterState());
        String metadataJson = serialize(request.getMetadata());
        String normalizedSourceType = defaultIfBlank(request.getSourceType(), "AUDIT");
        String normalizedModuleKey = defaultIfBlank(request.getModuleKey(), "AUDIT");
        String normalizedEntityType = defaultIfBlank(request.getEntityType(), "AUDIT_RECORD");
        String normalizedEntityKey = defaultIfBlank(request.getEntityKey(), normalizedModuleKey);
        String normalizedActionType = defaultIfBlank(request.getActionType(), "RECORDED");
        String evidenceTags = joinTags(request.getEvidenceTags());
        String eventHash = hash(
                previousHash,
                request.getTenantId(),
                normalizedSourceType,
                normalizedModuleKey,
                normalizedEntityType,
                normalizedEntityKey,
                normalizedActionType,
                request.getTitle(),
                trimToNull(request.getSummary()),
                trimToNull(request.getActor()),
                trimToNull(request.getReason()),
                trimToNull(request.getStatus()),
                trimToNull(request.getReferenceKey()),
                trimToNull(request.getControlFamily()),
                evidenceTags,
                beforeState,
                afterState,
                metadataJson,
                eventAt.toString()
        );

        auditLedgerEntryRepository.save(AuditLedgerEntry.builder()
                .tenantId(request.getTenantId())
                .sourceType(normalizedSourceType)
                .moduleKey(normalizedModuleKey)
                .entityType(normalizedEntityType)
                .entityKey(normalizedEntityKey)
                .actionType(normalizedActionType)
                .title(request.getTitle())
                .summary(trimToNull(request.getSummary()))
                .actor(trimToNull(request.getActor()))
                .reason(trimToNull(request.getReason()))
                .status(trimToNull(request.getStatus()))
                .referenceKey(trimToNull(request.getReferenceKey()))
                .controlFamily(trimToNull(request.getControlFamily()))
                .evidenceTags(evidenceTags)
                .beforeState(beforeState)
                .afterState(afterState)
                .metadataJson(metadataJson)
                .eventAt(eventAt)
                .previousHash(previousHash)
                .eventHash(eventHash)
                .build());
    }

    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            String trimmed = trimToNull(text);
            if (trimmed == null) {
                return null;
            }
            if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                    || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                return trimmed;
            }
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return Objects.toString(value, null);
        }
    }

    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return tags.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
    }

    private String hash(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                digest.update(defaultIfBlank(value, "-").getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            return Integer.toHexString(java.util.Arrays.hashCode(values));
        }
    }

    private boolean isBlank(String value) {
        return trimToNull(value) == null;
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }
}
