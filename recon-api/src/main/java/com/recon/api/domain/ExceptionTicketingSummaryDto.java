package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionTicketingSummaryDto {
    private long channelCount;
    private long activeChannelCount;
    private long recentTicketCount;
    private long recentCommunicationCount;
    private long failedDeliveries;
}
