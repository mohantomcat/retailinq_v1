package com.recon.publisher.repository;

import com.recon.integration.jdbc.IntegrationRunJournalSupport;
import com.recon.integration.model.CanonicalIntegrationEnvelope;
import com.recon.publisher.service.XstoreIntegrationContract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class IntegrationRunJournalRepository {

    private final IntegrationRunJournalSupport support;

    public IntegrationRunJournalRepository(@Value("${runtime-config.datasource.url}") String url,
                                           @Value("${runtime-config.datasource.username}") String username,
                                           @Value("${runtime-config.datasource.password}") String password,
                                           @Value("${runtime-config.datasource.driver-class-name}") String driverClassName) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        this.support = new IntegrationRunJournalSupport(new org.springframework.jdbc.core.JdbcTemplate(dataSource));
    }

    public UUID startRun(String tenantId,
                         XstoreIntegrationContract contract,
                         String triggerType) {
        return support.startRun(tenantId, contract, triggerType);
    }

    public UUID startStep(UUID runId,
                          String stepKey,
                          String stepLabel,
                          int stepOrder) {
        return support.startStep(runId, stepKey, stepLabel, stepOrder);
    }

    public void recordPublishedMessage(String tenantId,
                                       UUID runId,
                                       XstoreIntegrationContract contract,
                                       CanonicalIntegrationEnvelope envelope,
                                       String payloadSnapshotJson) {
        support.recordPublishedMessage(tenantId, runId, contract, envelope, payloadSnapshotJson);
    }

    public void recordFailedMessage(String tenantId,
                                    UUID runId,
                                    XstoreIntegrationContract contract,
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
