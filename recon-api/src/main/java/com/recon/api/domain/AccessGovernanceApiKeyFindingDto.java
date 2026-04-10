package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AccessGovernanceApiKeyFindingDto {
    private UUID id;
    private String keyName;
    private String keyPrefix;
    private boolean active;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private List<String> findingTypes;
}
