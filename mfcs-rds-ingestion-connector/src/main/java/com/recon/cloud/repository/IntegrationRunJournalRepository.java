package com.recon.cloud.repository;

import com.recon.cloud.service.MfcsIntegrationContract;
import com.recon.integration.jdbc.IntegrationRunJournalSupport;
import com.recon.integration.model.CanonicalIntegrationEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class IntegrationRunJournalRepository {

    private final IntegrationRunJournalSupport support;

    public IntegrationRunJournalRepository(JdbcTemplate jdbcTemplate) {
        this.support = new IntegrationRunJournalSupport(jdbcTemplate);
    }

    public UUID startRun(String tenantId,
                         MfcsIntegrationContract contract,
                         String triggerType) {
        return support.startRun(tenantId, contract, triggerType);
    }

    public UUID startStep(UUID runId,
                          String stepKey,
                          String stepLabel,
                          int stepOrder) {
        return support.startStep(runId, stepKey, stepLabel, stepOrder);
    }

    public UUID recordPublishedMessage(String tenantId,
                                       UUID runId,
                                       MfcsIntegrationContract contract,
                                       CanonicalIntegrationEnvelope envelope,
                                       String payloadSnapshotJson) {
        return support.recordPublishedMessage(tenantId, runId, contract, envelope, payloadSnapshotJson);
    }

    public void recordFailedMessage(String tenantId,
                                    UUID runId,
                                    MfcsIntegrationContract contract,
                                    String messageId,
                                    String traceId,
                                    String businessKey,
                                    String documentId,
                                    String payloadSnapshotJson,
                                    String errorType,
                                    String errorCode,
                                    String errorMessage,
                                    boolean retryable) {
        support.recordFailedMessage(
                tenantId,
                runId,
                contract,
                messageId,
                traceId,
                businessKey,
                documentId,
                payloadSnapshotJson,
                errorType,
                errorCode,
                errorMessage,
                retryable
        );
    }

    public void completeStep(UUID stepId,
                             int processedCount,
                             int errorCount,
                             String detailMessage,
                             String stepStatus) {
        support.completeStep(stepId, processedCount, errorCount, detailMessage, stepStatus);
    }

    public void completeRun(UUID runId,
                            int sourceRecordCount,
                            int publishedRecordCount,
                            int errorCount,
                            String summary,
                            String runStatus) {
        support.completeRun(runId, sourceRecordCount, publishedRecordCount, errorCount, summary, runStatus);
    }
}
