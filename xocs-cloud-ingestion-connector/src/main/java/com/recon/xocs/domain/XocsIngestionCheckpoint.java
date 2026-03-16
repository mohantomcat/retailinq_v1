package com.recon.xocs.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XocsIngestionCheckpoint {
    private String connectorName;
    private String sourceName;
    private String tenantId;
    private Long lastCursorId;
    private Timestamp lastSuccessTimestamp;
    private String lastPollStatus;
    private String lastErrorMessage;
}
