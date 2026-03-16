package com.recon.api.service;

import com.recon.api.domain.ActivityFeedResponse;
import com.recon.api.domain.ActivityRecordDto;
import com.recon.api.domain.ActivitySummaryDto;
import com.recon.api.repository.ActivityFeedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityFeedService {

    private final ActivityFeedRepository activityFeedRepository;

    public ActivityFeedResponse getActivity(String tenantId,
                                            String moduleKey,
                                            String sourceType,
                                            String actor,
                                            LocalDate fromDate,
                                            LocalDate toDate,
                                            Integer limit) {
        int resolvedLimit = limit == null || limit <= 0 ? 200 : Math.min(limit, 500);
        List<ActivityRecordDto> records = activityFeedRepository.findActivity(
                tenantId,
                moduleKey,
                sourceType,
                actor,
                fromDate,
                toDate,
                resolvedLimit
        );

        ActivitySummaryDto summary = ActivitySummaryDto.builder()
                .totalRecords(records.size())
                .operationsCount(records.stream().filter(record -> "OPERATIONS".equals(record.getSourceType())).count())
                .configurationCount(records.stream().filter(record -> "CONFIGURATION".equals(record.getSourceType())).count())
                .exceptionCount(records.stream().filter(record -> "EXCEPTION".equals(record.getSourceType())).count())
                .alertCount(records.stream().filter(record -> "ALERT".equals(record.getSourceType())).count())
                .build();

        return ActivityFeedResponse.builder()
                .summary(summary)
                .records(records)
                .build();
    }
}
