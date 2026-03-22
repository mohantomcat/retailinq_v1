package com.recon.api.service;

import com.recon.api.domain.ActivityFeedResponse;
import com.recon.api.domain.ActivityRecordDto;
import com.recon.api.domain.ActivitySummaryDto;
import com.recon.api.repository.AuditLedgerQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityFeedService {

    private final AuditLedgerQueryRepository auditLedgerQueryRepository;

    public ActivityFeedResponse getActivity(String tenantId,
                                            String moduleKey,
                                            String sourceType,
                                            String actor,
                                            LocalDate fromDate,
                                            LocalDate toDate,
                                            boolean includeArchived,
                                            Integer limit) {
        int resolvedLimit = limit == null || limit <= 0 ? 200 : Math.min(limit, 500);
        List<ActivityRecordDto> records = auditLedgerQueryRepository.findEntries(
                tenantId,
                moduleKey,
                sourceType,
                actor,
                fromDate,
                toDate,
                includeArchived,
                resolvedLimit
        ).stream().map(record -> ActivityRecordDto.builder()
                .sourceType(record.getSourceType())
                .moduleKey(record.getModuleKey())
                .actionType(record.getActionType())
                .actor(record.getActor())
                .title(record.getTitle())
                .summary(record.getSummary())
                .reason(record.getReason())
                .referenceKey(record.getReferenceKey())
                .status(record.getStatus())
                .controlFamily(record.getControlFamily())
                .eventHash(record.getEventHash())
                .archived(record.isArchived())
                .eventTimestamp(record.getEventTimestamp())
                .build())
                .toList();

        ActivitySummaryDto summary = ActivitySummaryDto.builder()
                .totalRecords(records.size())
                .operationsCount(records.stream().filter(record -> "OPERATIONS".equals(record.getSourceType())).count())
                .configurationCount(records.stream().filter(record -> "CONFIGURATION".equals(record.getSourceType())).count())
                .exceptionCount(records.stream().filter(record -> "EXCEPTION".equals(record.getSourceType())).count())
                .alertCount(records.stream().filter(record -> "ALERT".equals(record.getSourceType())).count())
                .securityCount(records.stream().filter(record -> "SECURITY".equals(record.getSourceType())).count())
                .slaCount(records.stream().filter(record -> "SLA".equals(record.getSourceType())).count())
                .complianceCount(records.stream().filter(record -> "COMPLIANCE".equals(record.getSourceType())).count())
                .archivedCount(records.stream().filter(ActivityRecordDto::isArchived).count())
                .build();

        return ActivityFeedResponse.builder()
                .summary(summary)
                .records(records)
                .build();
    }
}
