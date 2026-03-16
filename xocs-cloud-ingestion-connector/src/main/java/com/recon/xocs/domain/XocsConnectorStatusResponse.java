package com.recon.xocs.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XocsConnectorStatusResponse {
    private boolean enabled;
    private String connectorName;
    private String sourceName;
    private String tenantId;
    private String checkpointStatus;
    private Long lastCursorId;
    private Timestamp lastSuccessTimestamp;
    private String lastErrorMessage;
    private Map<String, Long> ingestionCounts;
    private long errorCount;
    private Timestamp oldestReadyTimestamp;
    private Timestamp oldestFailedTimestamp;
}
