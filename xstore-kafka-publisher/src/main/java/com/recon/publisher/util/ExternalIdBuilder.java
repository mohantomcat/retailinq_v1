package com.recon.publisher.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class ExternalIdBuilder {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Format: {5-digit storeId}{3-digit wkstnId}{6-digit transSeq}{yyyyMMdd}
     * Total : 22 characters, no separators.
     * Example: store=1001, wkstn=1, seq=12345, date=2024-01-15
     * -> "0100100101234520240115"
     */
    public String build(long storeId, long wkstnId,
                        long transSeq, LocalDate businessDate) {
        return String.format("%05d%03d%06d%s",
                storeId,
                wkstnId,
                transSeq,
                businessDate.format(DATE_FORMAT));
    }

    public String buildTransactionKey(long orgId, String externalId) {
        return orgId + "|" + externalId;
    }

    public ExternalIdComponents parse(String externalId) {
        if (externalId == null || externalId.length() != 22) {
            throw new IllegalArgumentException(
                    "Invalid EXTERNAL_ID length: expected 22, got " +
                            (externalId == null ? "null" : externalId.length()));
        }
        return ExternalIdComponents.builder()
                .storeId(Long.parseLong(externalId.substring(0, 5)))
                .wkstnId(Long.parseLong(externalId.substring(5, 8)))
                .transSeq(Long.parseLong(externalId.substring(8, 14)))
                .businessDate(LocalDate.parse(
                        externalId.substring(14, 22), DATE_FORMAT))

                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExternalIdComponents {
        private long storeId;
        private long wkstnId;
        private long transSeq;
        private LocalDate businessDate;
    }
}