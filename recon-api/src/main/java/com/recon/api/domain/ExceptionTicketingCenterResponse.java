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
public class ExceptionTicketingCenterResponse {
    private ExceptionTicketingSummaryDto summary;
    private List<ExceptionIntegrationChannelDto> channels;
    private List<ExceptionExternalTicketDto> recentTickets;
    private List<ExceptionOutboundCommunicationDto> recentCommunications;
}
