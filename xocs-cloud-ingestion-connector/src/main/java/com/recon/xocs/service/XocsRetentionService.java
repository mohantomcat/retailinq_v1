package com.recon.xocs.service;

import com.recon.xocs.config.XocsConnectorProperties;
import com.recon.xocs.repository.XocsErrorRepository;
import com.recon.xocs.repository.XocsRawRepository;
import com.recon.xocs.repository.XocsTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class XocsRetentionService {

    private final XocsConnectorProperties properties;
    private final XocsRawRepository rawRepository;
    private final XocsTransactionRepository transactionRepository;
    private final XocsErrorRepository errorRepository;
    private final XocsRuntimeConfigService runtimeConfigService;

    @Scheduled(fixedDelayString = "${xocs.connector.retention-interval-ms:86400000}")
    public void purgeExpiredData() {
        if (!runtimeConfigService.getBoolean("XOCS_CONNECTOR_ENABLED", properties.isEnabled())) {
            return;
        }
        int rawPurged = rawRepository.purgeOlderThanDays(
                runtimeConfigService.getInt("XOCS_RAW_RETENTION_DAYS", properties.getRawRetentionDays()));
        int errorPurged = errorRepository.purgeOlderThanDays(
                runtimeConfigService.getInt("XOCS_ERROR_RETENTION_DAYS", properties.getErrorRetentionDays()));
        int transactionPurged = transactionRepository.purgeOlderThanDays(
                runtimeConfigService.getInt("XOCS_NORMALIZED_RETENTION_DAYS", properties.getNormalizedRetentionDays()));
        if (rawPurged > 0 || errorPurged > 0 || transactionPurged > 0) {
            log.info("Purged expired XOCS staged data raw={}, errors={}, transactions={}",
                    rawPurged, errorPurged, transactionPurged);
        }
    }
}
