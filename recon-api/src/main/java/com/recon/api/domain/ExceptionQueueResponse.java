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
public class ExceptionQueueResponse {
    private ExceptionQueueSummaryDto summary;
    private List<ExceptionStoreIncidentDto> storeIncidents;
    private List<ExceptionQueueItemDto> items;
}
