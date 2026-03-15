package com.recon.cloud.service;

import com.recon.cloud.config.CloudConnectorProperties;
import com.recon.cloud.repository.CloudErrorRepository;
import com.recon.cloud.repository.CloudRawRepository;
import com.recon.cloud.repository.CloudTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CloudRetentionService {

    private final CloudConnectorProperties properties;
    private final CloudRawRepository rawRepository;
    private final CloudTransactionRepository transactionRepository;
    private final CloudErrorRepository errorRepository;
    private final CloudRuntimeConfigService runtimeConfigService;

    @Scheduled(fixedDelayString = "${cloud.connector.retention-interval-ms:86400000}")
    public void purgeExpiredData() {
        if (!runtimeConfigService.getBoolean("CLOUD_CONNECTOR_ENABLED", properties.isEnabled())) {
            return;
        }
        int rawPurged = rawRepository.purgeOlderThanDays(
                runtimeConfigService.getInt("CLOUD_RAW_RETENTION_DAYS", properties.getRawRetentionDays()));
        int errorPurged = errorRepository.purgeOlderThanDays(
                runtimeConfigService.getInt("CLOUD_ERROR_RETENTION_DAYS", properties.getErrorRetentionDays()));
        int transactionPurged = transactionRepository.purgeOlderThanDays(
                runtimeConfigService.getInt("CLOUD_NORMALIZED_RETENTION_DAYS", properties.getNormalizedRetentionDays()));
        if (rawPurged > 0 || errorPurged > 0 || transactionPurged > 0) {
            log.info("Purged expired SIOCS staged data raw={}, errors={}, transactions={}",
                    rawPurged, errorPurged, transactionPurged);
        }
    }
}
