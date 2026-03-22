package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditRetentionCenterResponse {
    private AuditRetentionPolicyDto policy;
    private long liveEntries;
    private long archivedEntries;
    private long eligibleForArchive;
    private String lastArchiveAt;
    private List<AuditArchiveBatchDto> recentBatches;
}
