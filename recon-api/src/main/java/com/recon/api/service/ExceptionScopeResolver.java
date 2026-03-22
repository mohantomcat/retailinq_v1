package com.recon.api.service;

import com.recon.api.domain.ExceptionCase;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

@Component
public class ExceptionScopeResolver {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    public String resolveStoreId(ExceptionCase exceptionCase) {
        String existing = trimToNull(exceptionCase.getStoreId());
        if (existing != null) {
            return existing;
        }
        ParsedScope parsedScope = parse(exceptionCase.getTransactionKey());
        return parsedScope.storeId();
    }

    public String resolveWkstnId(ExceptionCase exceptionCase) {
        String existing = trimToNull(exceptionCase.getWkstnId());
        if (existing != null) {
            return existing;
        }
        ParsedScope parsedScope = parse(exceptionCase.getTransactionKey());
        return parsedScope.wkstnId();
    }

    public LocalDate resolveBusinessDate(ExceptionCase exceptionCase) {
        if (exceptionCase.getBusinessDate() != null) {
            return exceptionCase.getBusinessDate();
        }
        ParsedScope parsedScope = parse(exceptionCase.getTransactionKey());
        return parsedScope.businessDate();
    }

    private ParsedScope parse(String transactionKey) {
        String normalized = trimToNull(transactionKey);
        if (normalized == null) {
            return ParsedScope.empty();
        }

        String[] parts = normalized.split("\\|");
        if (parts.length >= 2 && parts[1].matches("\\d{22}")) {
            String externalId = parts[1];
            return new ParsedScope(
                    normalizeNumeric(externalId.substring(0, 5)),
                    normalizeNumeric(externalId.substring(5, 8)),
                    parseDate(externalId.substring(14, 22))
            );
        }

        if (parts.length >= 5) {
            return new ParsedScope(
                    normalizeNumeric(parts[1]),
                    normalizeNumeric(parts[3]),
                    parseDate(parts[2])
            );
        }

        return ParsedScope.empty();
    }

    private String normalizeNumeric(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        String normalized = trimmed.replaceFirst("^0+(?!$)", "");
        return normalized.isBlank() ? "0" : normalized;
    }

    private LocalDate parseDate(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            if (trimmed.matches("\\d{8}")) {
                return LocalDate.parse(trimmed, BASIC_DATE);
            }
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ParsedScope(String storeId, String wkstnId, LocalDate businessDate) {
        private static ParsedScope empty() {
            return new ParsedScope(null, null, null);
        }
    }
}
