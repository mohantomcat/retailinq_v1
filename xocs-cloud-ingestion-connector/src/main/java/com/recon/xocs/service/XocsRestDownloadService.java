package com.recon.xocs.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.publisher.util.ExternalIdBuilder;
import com.recon.xocs.config.XocsConnectorProperties;
import com.recon.xocs.domain.XocsApiLineItem;
import com.recon.xocs.domain.XocsApiPage;
import com.recon.xocs.domain.XocsApiTransaction;
import com.recon.xocs.domain.XocsIngestionCheckpoint;
import com.recon.xocs.repository.XocsCheckpointRepository;
import com.recon.xocs.repository.XocsErrorRepository;
import com.recon.xocs.repository.XocsLineRepository;
import com.recon.xocs.repository.XocsRawRepository;
import com.recon.xocs.repository.XocsTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class XocsRestDownloadService {

    private final XocsConnectorProperties properties;
    private final XocsCheckpointRepository checkpointRepository;
    private final XocsRestClient restClient;
    private final XocsRawRepository rawRepository;
    private final XocsTransactionRepository transactionRepository;
    private final XocsLineRepository lineRepository;
    private final XocsErrorRepository errorRepository;
    private final ObjectMapper objectMapper;
    private final ExternalIdBuilder externalIdBuilder;
    private final XocsRuntimeConfigService runtimeConfigService;

    @Scheduled(fixedDelayString = "${xocs.connector.download-interval-ms:900000}")
    public void download() {
        if (!runtimeConfigService.getBoolean("XOCS_SCHEDULER_ENABLED", properties.isSchedulerEnabled())) {
            return;
        }
        runDownloadCycle();
    }

    public void runDownloadCycle() {
        if (!runtimeConfigService.getBoolean("XOCS_CONNECTOR_ENABLED", properties.isEnabled())) {
            return;
        }

        XocsIngestionCheckpoint checkpoint = checkpointRepository.findOrCreate(
                properties.getConnectorName(),
                properties.getSourceName(),
                properties.getTenantId());

        Instant checkpointInstant = checkpoint.getLastSuccessTimestamp() == null
                ? Instant.EPOCH
                : checkpoint.getLastSuccessTimestamp().toInstant();
        int overlapMinutes = runtimeConfigService.getInt("XOCS_OVERLAP_MINUTES", properties.getOverlapMinutes());
        int pollBusinessDays = runtimeConfigService.getInt("XOCS_POLL_BUSINESS_DAYS", properties.getPollBusinessDays());
        int batchSize = runtimeConfigService.getInt("XOCS_BATCH_SIZE", properties.getBatchSize());
        Timestamp fromTimestamp = Timestamp.from(
                checkpointInstant.minus(overlapMinutes, ChronoUnit.MINUTES));
        checkpointRepository.markStarted(properties.getConnectorName());
        Instant maxSeenTimestamp = checkpointInstant;

        try {
            for (int dayOffset = 0; dayOffset < pollBusinessDays; dayOffset++) {
                LocalDate businessDate = LocalDate.now().minusDays(dayOffset);
                Long lastCursorId = 0L;
                while (true) {
                    XocsApiPage page = restClient.fetchTransactions(businessDate, lastCursorId, fromTimestamp, batchSize);
                    if (page.getRecords() == null || page.getRecords().isEmpty()) {
                        break;
                    }

                    persistPage(fromTimestamp, lastCursorId, properties.getBatchSize(), page.getRecords());

                    XocsApiTransaction last = page.getRecords().get(page.getRecords().size() - 1);
                    lastCursorId = last.getTransSeq();
                    if (last.getUpdateDateTime() != null && last.getUpdateDateTime().isAfter(maxSeenTimestamp)) {
                        maxSeenTimestamp = last.getUpdateDateTime();
                    }

                    if (!page.isHasMore()) {
                        break;
                    }
                }
            }
            checkpointRepository.advance(properties.getConnectorName(), 0L, Timestamp.from(maxSeenTimestamp));
        } catch (Exception e) {
            checkpointRepository.markFailed(properties.getConnectorName(), e.getMessage());
            log.error("XOCS download failed: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void persistPage(Timestamp requestFromUpdateTs,
                            Long requestLastCursorId,
                            int requestLimit,
                            List<XocsApiTransaction> records) {
        long rawId = persistRawPage(requestFromUpdateTs, requestLastCursorId, requestLimit, records);
        for (XocsApiTransaction record : records) {
            try {
                normalize(record);
                validateRecord(record);
                long transactionId = transactionRepository.upsertTransaction(
                        properties.getTenantId(),
                        properties.getSourceName(),
                        rawId,
                        record);
                lineRepository.replaceLines(
                        transactionId,
                        record.getTransactionKey(),
                        record.getOrganizationId(),
                        record.getRtlLocId(),
                        record.getBusinessDate(),
                        record.getWkstnId(),
                        record.getTransSeq(),
                        record.getUpdateDateTime(),
                        record.getLineItems());
            } catch (Exception e) {
                saveError(record, "INGESTION", "INGESTION_ERROR", e.getMessage(), true);
            }
        }
    }

    private long persistRawPage(Timestamp requestFromUpdateTs,
                                Long requestLastCursorId,
                                int requestLimit,
                                List<XocsApiTransaction> records) {
        try {
            String payload = objectMapper.writeValueAsString(records);
            return rawRepository.insert(
                    properties.getTenantId(),
                    properties.getConnectorName(),
                    properties.getSourceName(),
                    requestFromUpdateTs,
                    requestLastCursorId,
                    requestLimit,
                    payload,
                    records.size(),
                    200);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize XOCS raw page", e);
        }
    }

    private void normalize(XocsApiTransaction record) {
        if (record.getExternalId() == null
                && record.getRtlLocId() != null
                && record.getWkstnId() != null
                && record.getTransSeq() != null
                && record.getBusinessDate() != null) {
            record.setExternalId(externalIdBuilder.build(
                    record.getRtlLocId(),
                    record.getWkstnId(),
                    record.getTransSeq(),
                    record.getBusinessDate()));
        }
        if (record.getTransactionKey() == null || record.getTransactionKey().isBlank()) {
            record.setTransactionKey(externalIdBuilder.buildTransactionKey(record.getOrganizationId(), record.getExternalId()));
        }
        if (record.getSourceRecordKey() == null || record.getSourceRecordKey().isBlank()) {
            record.setSourceRecordKey(record.getTransactionKey());
        }
        if (record.getLineItems() != null) {
            for (XocsApiLineItem lineItem : record.getLineItems()) {
                normalizeLine(record, lineItem);
            }
        }
        if (record.getLineCount() == null) {
            record.setLineCount(record.getLineItems() == null ? 0 : record.getLineItems().size());
        }
        if (record.getDistinctItemCount() == null && record.getLineItems() != null) {
            record.setDistinctItemCount((int) record.getLineItems().stream()
                    .map(XocsApiLineItem::getItemId)
                    .filter(itemId -> itemId != null && !itemId.isBlank())
                    .distinct()
                    .count());
        }
        if (record.getTotalItemQty() == null && record.getLineItems() != null) {
            record.setTotalItemQty(record.getLineItems().stream()
                    .map(XocsApiLineItem::getNormalizedQuantity)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
    }

    private void normalizeLine(XocsApiTransaction record, XocsApiLineItem lineItem) {
        if (lineItem.getLineBusinessType() == null || lineItem.getLineBusinessType().isBlank()) {
            int voidFlag = lineItem.getVoidFlag() == null ? 0 : lineItem.getVoidFlag();
            int returnFlag = lineItem.getReturnFlag() == null ? 0 : lineItem.getReturnFlag();
            if (voidFlag == 1 && returnFlag == 1) {
                lineItem.setLineBusinessType("VOID_RETURN");
            } else if (voidFlag == 1) {
                lineItem.setLineBusinessType("VOID_SALE");
            } else if (returnFlag == 1) {
                lineItem.setLineBusinessType("RETURN");
            } else {
                lineItem.setLineBusinessType("SALE");
            }
        }
        if (lineItem.getNormalizedQuantity() == null && lineItem.getRawQuantity() != null) {
            lineItem.setNormalizedQuantity(lineItem.getRawQuantity().abs());
        }
        if (lineItem.getLineUpdateDate() == null) {
            lineItem.setLineUpdateDate(record.getUpdateDateTime());
        }
    }

    private void validateRecord(XocsApiTransaction record) {
        if (record == null) {
            throw new IllegalArgumentException("Record is null");
        }
        if (record.getOrganizationId() == null
                || record.getRtlLocId() == null
                || record.getBusinessDate() == null
                || record.getWkstnId() == null
                || record.getTransSeq() == null) {
            throw new IllegalArgumentException("Missing transaction identity fields");
        }
        if (record.getExternalId() == null || record.getExternalId().isBlank()) {
            throw new IllegalArgumentException("Missing externalId");
        }
        if (record.getTransactionKey() == null || record.getTransactionKey().isBlank()) {
            throw new IllegalArgumentException("Missing transactionKey");
        }
    }

    private void saveError(XocsApiTransaction record,
                           String stage,
                           String errorCode,
                           String errorMessage,
                           boolean retryable) {
        try {
            errorRepository.save(
                    properties.getConnectorName(),
                    properties.getTenantId(),
                    properties.getSourceName(),
                    record == null ? null : record.getSourceRecordKey(),
                    record == null ? "{}" : objectMapper.writeValueAsString(record),
                    stage,
                    errorCode,
                    errorMessage,
                    retryable);
        } catch (JsonProcessingException ignored) {
            errorRepository.save(
                    properties.getConnectorName(),
                    properties.getTenantId(),
                    properties.getSourceName(),
                    record == null ? null : record.getSourceRecordKey(),
                    "{}",
                    stage,
                    errorCode,
                    errorMessage,
                    retryable);
        }
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? Timestamp.from(Instant.now()) : Timestamp.from(instant);
    }
}
