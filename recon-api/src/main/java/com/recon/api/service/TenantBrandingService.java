package com.recon.api.service;

import com.recon.api.domain.AuditLedgerWriteRequest;
import com.recon.api.domain.SaveTenantBrandingRequest;
import com.recon.api.domain.TenantBrandingConfigEntity;
import com.recon.api.domain.TenantBrandingDto;
import com.recon.api.repository.TenantBrandingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TenantBrandingService {

    private static final String DEFAULT_APP_NAME = "RetailINQ";
    private static final String DEFAULT_PRIMARY_COLOR = "#3F6FD8";
    private static final String DEFAULT_SECONDARY_COLOR = "#5F7CE2";
    private static final int MAX_LOGO_DATA_LENGTH = 800_000;
    private static final Pattern HEX_COLOR_PATTERN =
            Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final TenantBrandingRepository tenantBrandingRepository;
    private final AuditLedgerService auditLedgerService;

    @Transactional(readOnly = true)
    public TenantBrandingDto getCurrentBranding(String tenantId) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        TenantBrandingConfigEntity branding = resolveBranding(normalizedTenantId);
        return toDto(branding, isCustomized(branding));
    }

    @Transactional
    public TenantBrandingDto saveBranding(String tenantId,
                                          SaveTenantBrandingRequest request,
                                          String actor) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        TenantBrandingConfigEntity current = resolveBranding(normalizedTenantId);
        TenantBrandingDto before = toDto(current, isCustomized(current));
        SaveTenantBrandingRequest safeRequest = request != null
                ? request
                : SaveTenantBrandingRequest.builder().build();

        current.setAppName(DEFAULT_APP_NAME);
        current.setLightLogoData(normalizeLogoData(safeRequest.getLightLogoData(), "light"));
        current.setDarkLogoData(normalizeLogoData(safeRequest.getDarkLogoData(), "dark"));
        current.setPrimaryColor(DEFAULT_PRIMARY_COLOR);
        current.setSecondaryColor(DEFAULT_SECONDARY_COLOR);
        current.setUpdatedBy(defaultActor(actor));
        current.setUpdatedAt(LocalDateTime.now());

        TenantBrandingConfigEntity saved = tenantBrandingRepository.save(current);
        TenantBrandingDto after = toDto(saved, isCustomized(saved));
        recordAudit(normalizedTenantId, before, after, actor);
        return after;
    }

    private TenantBrandingConfigEntity resolveBranding(String tenantId) {
        return tenantBrandingRepository.findById(tenantId)
                .orElseGet(() -> TenantBrandingConfigEntity.builder()
                        .tenantId(tenantId)
                        .appName(DEFAULT_APP_NAME)
                        .primaryColor(DEFAULT_PRIMARY_COLOR)
                        .secondaryColor(DEFAULT_SECONDARY_COLOR)
                        .updatedBy("system")
                        .build());
    }

    private boolean isCustomized(TenantBrandingConfigEntity entity) {
        return trimToNull(entity.getLightLogoData()) != null
                || trimToNull(entity.getDarkLogoData()) != null;
    }

    private TenantBrandingDto toDto(TenantBrandingConfigEntity entity, boolean customized) {
        return TenantBrandingDto.builder()
                .tenantId(entity.getTenantId())
                .appName(normalizeAppName(entity.getAppName()))
                .lightLogoData(trimToNull(entity.getLightLogoData()))
                .darkLogoData(trimToNull(entity.getDarkLogoData()))
                .primaryColor(normalizeHexColor(entity.getPrimaryColor(), DEFAULT_PRIMARY_COLOR))
                .secondaryColor(normalizeHexColor(entity.getSecondaryColor(), DEFAULT_SECONDARY_COLOR))
                .customized(customized)
                .updatedAt(entity.getUpdatedAt())
                .updatedBy(defaultActor(entity.getUpdatedBy()))
                .build();
    }

    private void recordAudit(String tenantId,
                             TenantBrandingDto before,
                             TenantBrandingDto after,
                             String actor) {
        auditLedgerService.record(AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("CONFIG")
                .moduleKey("BRANDING")
                .entityType("TENANT_BRANDING")
                .entityKey(tenantId)
                .actionType("TENANT_BRANDING_UPDATED")
                .title("Tenant branding updated")
                .summary(after.getAppName())
                .actor(defaultActor(actor))
                .status("UPDATED")
                .referenceKey(tenantId)
                .controlFamily("CONFIG_CHANGE")
                .evidenceTags(List.of("BRANDING", "TENANT"))
                .beforeState(before)
                .afterState(after)
                .build());
    }

    private String normalizeTenantId(String tenantId) {
        String normalized = trimToNull(tenantId);
        if (normalized == null) {
            return "tenant-india";
        }
        return normalized;
    }

    private String normalizeAppName(String appName) {
        return DEFAULT_APP_NAME;
    }

    private String normalizeHexColor(String value, String fallback) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return fallback;
        }
        String normalized = trimmed.toUpperCase(Locale.ROOT);
        if (!HEX_COLOR_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid brand color: " + value);
        }
        return normalized;
    }

    private String normalizeLogoData(String value, String slot) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.length() > MAX_LOGO_DATA_LENGTH) {
            throw new IllegalArgumentException("Brand logo is too large: " + slot);
        }
        if (!trimmed.startsWith("data:image/") || !trimmed.contains(",")) {
            throw new IllegalArgumentException("Brand logo must be a valid image data URL");
        }
        String metadata = trimmed.substring(5, trimmed.indexOf(',')).toLowerCase(Locale.ROOT);
        if (!(metadata.startsWith("image/png")
                || metadata.startsWith("image/svg+xml")
                || metadata.startsWith("image/jpeg")
                || metadata.startsWith("image/webp"))) {
            throw new IllegalArgumentException("Unsupported brand logo format");
        }
        return trimmed;
    }

    private String defaultActor(String actor) {
        String trimmed = trimToNull(actor);
        return trimmed == null ? "system" : trimmed;
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
