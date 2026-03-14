package com.recon.cloud.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloudIngestionCheckpoint {
    private String connectorName;
    private String sourceName;
    private String tenantId;
    private Long lastCursorId;
    private Timestamp lastSuccessTimestamp;
    private Timestamp lastPolledTimestamp;
    private String lastStatus;
    private String lastErrorMessage;
}
