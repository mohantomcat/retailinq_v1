package com.recon.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.api.domain.CreateExceptionExternalTicketRequest;
import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.ExceptionExternalTicket;
import com.recon.api.domain.ExceptionExternalTicketDto;
import com.recon.api.domain.ExceptionExternalTicketSyncEvent;
import com.recon.api.domain.ExceptionIntegrationChannel;
import com.recon.api.domain.ExceptionIntegrationChannelDto;
import com.recon.api.domain.ExceptionOutboundCommunication;
import com.recon.api.domain.ExceptionOutboundCommunicationDto;
import com.recon.api.domain.ExceptionTicketingCenterResponse;
import com.recon.api.domain.ExceptionTicketingSummaryDto;
import com.recon.api.domain.SaveExceptionIntegrationChannelRequest;
import com.recon.api.domain.SendExceptionCommunicationRequest;
import com.recon.api.domain.SyncExceptionExternalTicketRequest;
import com.recon.api.repository.ExceptionCaseRepository;
import com.recon.api.repository.ExceptionExternalTicketRepository;
import com.recon.api.repository.ExceptionExternalTicketSyncEventRepository;
import com.recon.api.repository.ExceptionIntegrationChannelRepository;
import com.recon.api.repository.ExceptionOutboundCommunicationRepository;
import com.recon.api.util.TimezoneConverter;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExceptionCollaborationService {

    private static final Set<String> TICKETING_TYPES = Set.of("SERVICENOW", "JIRA", "GENERIC_WEBHOOK");
    private static final Set<String> COMMUNICATION_TYPES = Set.of("EMAIL", "MICROSOFT_TEAMS", "GENERIC_WEBHOOK");
    private static final String CALLBACK_SECRET_HEADER = "X-RetailINQ-Sync-Secret";

    private final ExceptionIntegrationChannelRepository channelRepository;
    private final ExceptionExternalTicketRepository externalTicketRepository;
    private final ExceptionExternalTicketSyncEventRepository syncEventRepository;
    private final ExceptionOutboundCommunicationRepository communicationRepository;
    private final ExceptionCaseRepository caseRepository;
    private final ExceptionScopeResolver exceptionScopeResolver;
    private final TenantService tenantService;
    private final ExceptionSlaService exceptionSlaService;
    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;

    @Value("${app.alerting.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.alerting.email.from:no-reply@retailinq.local}")
    private String fromAddress;

    @Value("${app.alerting.email.from-name:RetailINQ Alerts}")
    private String fromName;

    @Value("${app.alerting.webhook.enabled:false}")
    private boolean webhookEnabled;

    @Value("${app.alerting.webhook.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${app.alerting.webhook.read-timeout-ms:10000}")
    private int readTimeoutMs;

    @Value("${app.alerting.webhook.app-base-url:http://localhost:5173}")
    private String appBaseUrl;

    @Value("${app.collaboration.callback-base-url:http://localhost:8090}")
    private String callbackBaseUrl;

    @Transactional(readOnly = true)
    public ExceptionTicketingCenterResponse getCenter(String tenantId, String reconView) {
        String normalizedReconView = normalize(reconView);
        List<ExceptionIntegrationChannel> channels = channelRepository.findByTenantIdOrderByActiveDescUpdatedAtDesc(tenantId).stream()
                .filter(matchesReconView(normalizedReconView))
                .toList();
        List<ExceptionExternalTicket> recentTickets = externalTicketRepository.findTop50ByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(ticket -> normalizedReconView == null || Objects.equals(normalizedReconView, normalize(ticket.getReconView())))
                .limit(25)
                .toList();
        List<ExceptionOutboundCommunication> recentCommunications = communicationRepository.findTop50ByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(record -> normalizedReconView == null || Objects.equals(normalizedReconView, normalize(record.getReconView())))
                .limit(25)
                .toList();

        return ExceptionTicketingCenterResponse.builder()
                .summary(ExceptionTicketingSummaryDto.builder()
                        .channelCount(channels.size())
                        .activeChannelCount(channels.stream().filter(ExceptionIntegrationChannel::isActive).count())
                        .recentTicketCount(recentTickets.size())
                        .recentCommunicationCount(recentCommunications.size())
                        .failedDeliveries(recentTickets.stream().filter(this::isFailed).count()
                                + recentCommunications.stream().filter(this::isFailed).count())
                        .build())
                .channels(channels.stream().map(this::toChannelDto).toList())
                .recentTickets(recentTickets.stream().map(this::toTicketDto).toList())
                .recentCommunications(recentCommunications.stream().map(this::toCommunicationDto).toList())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ExceptionIntegrationChannelDto> getActiveChannels(String tenantId,
                                                                  String reconView,
                                                                  String channelGroup) {
        return channelRepository.findActiveChannels(tenantId, normalize(reconView), normalize(channelGroup)).stream()
                .map(this::toChannelDto)
                .toList();
    }

    @Transactional
    public ExceptionIntegrationChannelDto saveChannel(String tenantId,
                                                      UUID channelId,
                                                      SaveExceptionIntegrationChannelRequest request,
                                                      String actorUsername) {
        if (request == null) {
            throw new IllegalArgumentException("Integration channel request is required");
        }
        ExceptionIntegrationChannel channel = channelId == null
                ? ExceptionIntegrationChannel.builder()
                .tenantId(tenantId)
                .createdBy(actorUsername)
                .updatedBy(actorUsername)
                .build()
                : channelRepository.findByIdAndTenantId(channelId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Integration channel not found"));

        String channelName = trimToNull(request.getChannelName());
        String channelType = normalize(request.getChannelType());
        String channelGroup = normalize(request.getChannelGroup());
        if (channelName == null || channelType == null || channelGroup == null) {
            throw new IllegalArgumentException("Channel name, type, and group are required");
        }
        if (!Set.of("TICKETING", "COMMUNICATION", "BOTH").contains(channelGroup)) {
            throw new IllegalArgumentException("Supported channel groups are TICKETING, COMMUNICATION, or BOTH");
        }

        if ("EMAIL".equals(channelType) && trimToNull(request.getRecipientEmail()) == null) {
            throw new IllegalArgumentException("Recipient email is required for email channels");
        }
        if (!"EMAIL".equals(channelType) && trimToNull(request.getEndpointUrl()) == null) {
            throw new IllegalArgumentException("Endpoint URL is required for non-email channels");
        }
        if ("JIRA".equals(channelType) && trimToNull(request.getDefaultProjectKey()) == null) {
            throw new IllegalArgumentException("Default project key is required for Jira channels");
        }
        boolean inboundSyncEnabled = request.getInboundSyncEnabled() != null
                ? request.getInboundSyncEnabled()
                : channel.isInboundSyncEnabled();
        String inboundSharedSecret = request.getInboundSharedSecret() != null
                ? trimToNull(request.getInboundSharedSecret())
                : channel.getInboundSharedSecret();
        if (inboundSyncEnabled && inboundSharedSecret == null) {
            throw new IllegalArgumentException("Inbound shared secret is required when bidirectional sync is enabled");
        }
        if (inboundSyncEnabled && !Set.of("TICKETING", "BOTH").contains(channelGroup)) {
            throw new IllegalArgumentException("Inbound sync requires a ticketing-capable channel");
        }
        boolean autoCreateOnCaseOpen = request.getAutoCreateOnCaseOpen() != null
                ? request.getAutoCreateOnCaseOpen()
                : channel.isAutoCreateOnCaseOpen();
        boolean autoCreateOnEscalation = request.getAutoCreateOnEscalation() != null
                ? request.getAutoCreateOnEscalation()
                : channel.isAutoCreateOnEscalation();
        if ((autoCreateOnCaseOpen || autoCreateOnEscalation)
                && !Set.of("TICKETING", "BOTH").contains(channelGroup)) {
            throw new IllegalArgumentException("Automatic ticket push requires a ticketing-capable channel");
        }

        channel.setChannelName(channelName);
        channel.setChannelType(channelType);
        channel.setChannelGroup(channelGroup);
        channel.setReconView(normalize(request.getReconView()));
        channel.setEndpointUrl(trimToNull(request.getEndpointUrl()));
        channel.setRecipientEmail(trimToNull(request.getRecipientEmail()));
        channel.setHeadersJson(trimToNull(request.getHeadersJson()));
        channel.setDefaultProjectKey(trimToNull(request.getDefaultProjectKey()));
        channel.setDefaultIssueType(trimToNull(request.getDefaultIssueType()));
        channel.setDescription(trimToNull(request.getDescription()));
        channel.setInboundSyncEnabled(inboundSyncEnabled);
        channel.setInboundSharedSecret(inboundSharedSecret);
        channel.setAutoCreateOnCaseOpen(autoCreateOnCaseOpen);
        channel.setAutoCreateOnEscalation(autoCreateOnEscalation);
        if (request.getActive() != null) {
            channel.setActive(request.getActive());
        } else if (channelId == null) {
            channel.setActive(true);
        }
        channel.setUpdatedBy(actorUsername);

        return toChannelDto(channelRepository.save(channel));
    }

    @Transactional(readOnly = true)
    public List<ExceptionExternalTicketDto> getCaseExternalTickets(ExceptionCase exceptionCase) {
        return getCaseExternalTicketRecords(exceptionCase).stream()
                .map(this::toTicketDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExceptionOutboundCommunicationDto> getCaseCommunications(ExceptionCase exceptionCase) {
        return getCaseCommunicationRecords(exceptionCase).stream()
                .map(this::toCommunicationDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExceptionExternalTicket> getCaseExternalTicketRecords(ExceptionCase exceptionCase) {
        return externalTicketRepository.findByExceptionCaseOrderByCreatedAtDesc(exceptionCase);
    }

    @Transactional(readOnly = true)
    public List<ExceptionOutboundCommunication> getCaseCommunicationRecords(ExceptionCase exceptionCase) {
        return communicationRepository.findByExceptionCaseOrderByCreatedAtDesc(exceptionCase);
    }

    @Transactional(readOnly = true)
    public List<ExceptionExternalTicketSyncEvent> getCaseTicketSyncEvents(ExceptionCase exceptionCase) {
        return syncEventRepository.findByExceptionCaseOrderBySyncedAtDesc(exceptionCase);
    }

    @Transactional(readOnly = true)
    public Map<String, List<ExceptionExternalTicketDto>> getIncidentExternalTickets(String tenantId,
                                                                                    Collection<String> incidentKeys) {
        if (incidentKeys == null || incidentKeys.isEmpty()) {
            return Map.of();
        }
        return externalTicketRepository.findByTenantIdAndIncidentKeyInOrderByCreatedAtDesc(tenantId, incidentKeys).stream()
                .collect(Collectors.groupingBy(
                        ExceptionExternalTicket::getIncidentKey,
                        Collectors.mapping(this::toTicketDto, Collectors.toList())));
    }

    @Transactional(readOnly = true)
    public Map<String, List<ExceptionOutboundCommunicationDto>> getIncidentCommunications(String tenantId,
                                                                                          Collection<String> incidentKeys) {
        if (incidentKeys == null || incidentKeys.isEmpty()) {
            return Map.of();
        }
        return communicationRepository.findByTenantIdAndIncidentKeyInOrderByCreatedAtDesc(tenantId, incidentKeys).stream()
                .collect(Collectors.groupingBy(
                        ExceptionOutboundCommunication::getIncidentKey,
                        Collectors.mapping(this::toCommunicationDto, Collectors.toList())));
    }

    @Transactional
    public ExceptionExternalTicketDto createCaseExternalTicket(String tenantId,
                                                               ExceptionCase exceptionCase,
                                                               CreateExceptionExternalTicketRequest request,
                                                               String actorUsername) {
        ExceptionIntegrationChannel channel = resolveChannel(tenantId, request != null ? request.getChannelId() : null, "TICKETING", exceptionCase.getReconView());
        CollaborationContext context = contextFromCase(exceptionCase);
        String summary = trimToNull(request != null ? request.getTicketSummary() : null);
        String description = trimToNull(request != null ? request.getTicketDescription() : null);
        TicketDeliveryResult result = sendTicket(channel, context, summary != null ? summary : defaultTicketSummary(context), description);
        ExceptionExternalTicket saved = externalTicketRepository.save(ExceptionExternalTicket.builder()
                .tenantId(tenantId)
                .exceptionCase(exceptionCase)
                .transactionKey(exceptionCase.getTransactionKey())
                .incidentKey(null)
                .incidentTitle(null)
                .reconView(exceptionCase.getReconView())
                .storeId(context.storeId())
                .channel(channel)
                .channelName(channel.getChannelName())
                .channelType(channel.getChannelType())
                .ticketSummary(result.ticketSummary())
                .ticketDescription(result.ticketDescription())
                .externalReference(result.externalReference())
                .externalUrl(result.externalUrl())
                .deliveryStatus(result.deliveryStatus())
                .externalStatus("OPEN")
                .responseStatusCode(result.responseStatusCode())
                .requestPayload(result.requestPayload())
                .responsePayload(result.responsePayload())
                .errorMessage(result.errorMessage())
                .createdBy(actorUsername)
                .lastSyncedAt(LocalDateTime.now())
                .build());
        return toTicketDto(saved);
    }

    @Transactional
    public ExceptionOutboundCommunicationDto sendCaseCommunication(String tenantId,
                                                                   ExceptionCase exceptionCase,
                                                                   SendExceptionCommunicationRequest request,
                                                                   String actorUsername) {
        ExceptionIntegrationChannel channel = resolveChannel(tenantId, request != null ? request.getChannelId() : null, "COMMUNICATION", exceptionCase.getReconView());
        CollaborationContext context = contextFromCase(exceptionCase);
        CommunicationDeliveryResult result = sendCommunication(channel, context, trimToNull(request != null ? request.getSubject() : null), trimToNull(request != null ? request.getMessageBody() : null));
        ExceptionOutboundCommunication saved = communicationRepository.save(ExceptionOutboundCommunication.builder()
                .tenantId(tenantId)
                .exceptionCase(exceptionCase)
                .transactionKey(exceptionCase.getTransactionKey())
                .incidentKey(null)
                .incidentTitle(null)
                .reconView(exceptionCase.getReconView())
                .storeId(context.storeId())
                .channel(channel)
                .channelName(channel.getChannelName())
                .channelType(channel.getChannelType())
                .recipient(result.recipient())
                .subject(result.subject())
                .messageBody(result.messageBody())
                .deliveryStatus(result.deliveryStatus())
                .responseStatusCode(result.responseStatusCode())
                .requestPayload(result.requestPayload())
                .responsePayload(result.responsePayload())
                .errorMessage(result.errorMessage())
                .createdBy(actorUsername)
                .deliveredAt(result.deliveredAt())
                .build());
        return toCommunicationDto(saved);
    }

    @Transactional
    public ExceptionExternalTicketDto createIncidentExternalTicket(String tenantId,
                                                                   String reconView,
                                                                   CreateExceptionExternalTicketRequest request,
                                                                   String actorUsername) {
        ExceptionIntegrationChannel channel = resolveChannel(tenantId, request != null ? request.getChannelId() : null, "TICKETING", reconView);
        CollaborationContext context = contextFromIncident(reconView, request);
        TicketDeliveryResult result = sendTicket(channel, context, trimToNull(request.getTicketSummary()), trimToNull(request.getTicketDescription()));
        ExceptionExternalTicket saved = externalTicketRepository.save(ExceptionExternalTicket.builder()
                .tenantId(tenantId)
                .exceptionCase(null)
                .transactionKey(null)
                .incidentKey(context.incidentKey())
                .incidentTitle(context.contextTitle())
                .reconView(context.reconView())
                .storeId(context.storeId())
                .channel(channel)
                .channelName(channel.getChannelName())
                .channelType(channel.getChannelType())
                .ticketSummary(result.ticketSummary())
                .ticketDescription(result.ticketDescription())
                .externalReference(result.externalReference())
                .externalUrl(result.externalUrl())
                .deliveryStatus(result.deliveryStatus())
                .externalStatus("OPEN")
                .responseStatusCode(result.responseStatusCode())
                .requestPayload(result.requestPayload())
                .responsePayload(result.responsePayload())
                .errorMessage(result.errorMessage())
                .createdBy(actorUsername)
                .lastSyncedAt(LocalDateTime.now())
                .build());
        return toTicketDto(saved);
    }

    @Transactional
    public ExceptionOutboundCommunicationDto sendIncidentCommunication(String tenantId,
                                                                       String reconView,
                                                                       SendExceptionCommunicationRequest request,
                                                                       String actorUsername) {
        ExceptionIntegrationChannel channel = resolveChannel(tenantId, request != null ? request.getChannelId() : null, "COMMUNICATION", reconView);
        CollaborationContext context = contextFromIncident(reconView, request);
        CommunicationDeliveryResult result = sendCommunication(channel, context, trimToNull(request.getSubject()), trimToNull(request.getMessageBody()));
        ExceptionOutboundCommunication saved = communicationRepository.save(ExceptionOutboundCommunication.builder()
                .tenantId(tenantId)
                .exceptionCase(null)
                .transactionKey(null)
                .incidentKey(context.incidentKey())
                .incidentTitle(context.contextTitle())
                .reconView(context.reconView())
                .storeId(context.storeId())
                .channel(channel)
                .channelName(channel.getChannelName())
                .channelType(channel.getChannelType())
                .recipient(result.recipient())
                .subject(result.subject())
                .messageBody(result.messageBody())
                .deliveryStatus(result.deliveryStatus())
                .responseStatusCode(result.responseStatusCode())
                .requestPayload(result.requestPayload())
                .responsePayload(result.responsePayload())
                .errorMessage(result.errorMessage())
                .createdBy(actorUsername)
                .deliveredAt(result.deliveredAt())
                .build());
        return toCommunicationDto(saved);
    }

    @Transactional
    public void syncCaseTicketLifecycle(ExceptionCase exceptionCase,
                                        boolean newCase,
                                        String previousSeverity,
                                        String previousCaseStatus,
                                        String actorUsername) {
        if (exceptionCase == null || trimToNull(exceptionCase.getTenantId()) == null || trimToNull(exceptionCase.getReconView()) == null) {
            return;
        }
        boolean escalated = isEscalatedCase(previousSeverity, previousCaseStatus, exceptionCase);
        if (!newCase && !escalated) {
            return;
        }

        List<ExceptionIntegrationChannel> channels = channelRepository.findActiveChannels(
                        exceptionCase.getTenantId(),
                        normalize(exceptionCase.getReconView()),
                        "TICKETING")
                .stream()
                .filter(channel -> newCase && channel.isAutoCreateOnCaseOpen()
                        || escalated && channel.isAutoCreateOnEscalation())
                .toList();

        for (ExceptionIntegrationChannel channel : channels) {
            ExceptionExternalTicket existing = externalTicketRepository
                    .findTopByExceptionCaseAndChannelOrderByCreatedAtDesc(exceptionCase, channel)
                    .orElse(null);
            if (existing == null) {
                autoCreateCaseTicket(exceptionCase, channel, actorUsername, newCase ? "CASE_OPENED" : "CASE_ESCALATED");
                continue;
            }
            if (escalated) {
                pushEscalationUpdate(existing, exceptionCase, actorUsername);
            }
        }
    }

    @Transactional
    public void syncEscalatedCase(ExceptionCase exceptionCase, String actorUsername) {
        if (exceptionCase == null || trimToNull(exceptionCase.getTenantId()) == null || trimToNull(exceptionCase.getReconView()) == null) {
            return;
        }
        List<ExceptionIntegrationChannel> channels = channelRepository.findActiveChannels(
                        exceptionCase.getTenantId(),
                        normalize(exceptionCase.getReconView()),
                        "TICKETING")
                .stream()
                .filter(ExceptionIntegrationChannel::isAutoCreateOnEscalation)
                .toList();

        for (ExceptionIntegrationChannel channel : channels) {
            ExceptionExternalTicket existing = externalTicketRepository
                    .findTopByExceptionCaseAndChannelOrderByCreatedAtDesc(exceptionCase, channel)
                    .orElse(null);
            if (existing == null) {
                autoCreateCaseTicket(exceptionCase, channel, actorUsername, "CASE_ESCALATED");
                continue;
            }
            pushEscalationUpdate(existing, exceptionCase, actorUsername);
        }
    }

    @Transactional
    public ExceptionExternalTicketDto synchronizeExternalTicket(UUID channelId,
                                                               String providedSharedSecret,
                                                               SyncExceptionExternalTicketRequest request) {
        ExceptionIntegrationChannel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Integration channel not found"));
        if (!channel.isActive()) {
            throw new IllegalArgumentException("Selected integration channel is inactive");
        }
        if (!channel.isInboundSyncEnabled()) {
            throw new IllegalArgumentException("Inbound sync is not enabled for this channel");
        }
        String expectedSecret = trimToNull(channel.getInboundSharedSecret());
        if (expectedSecret == null || !Objects.equals(expectedSecret, trimToNull(providedSharedSecret))) {
            throw new IllegalArgumentException("Inbound sync secret is invalid");
        }
        ExceptionExternalTicket ticket = resolveSyncTarget(channel, request);
        LocalDateTime syncedAt = LocalDateTime.now();
        String externalStatus = normalizeExternalStatus(request != null ? request.getExternalStatus() : null);
        if (trimToNull(request != null ? request.getExternalReference() : null) != null) {
            ticket.setExternalReference(trimToNull(request.getExternalReference()));
        }
        if (trimToNull(request != null ? request.getExternalUrl() : null) != null) {
            ticket.setExternalUrl(trimToNull(request.getExternalUrl()));
        }
        if (trimToNull(request != null ? request.getTicketSummary() : null) != null) {
            ticket.setTicketSummary(trimToNull(request.getTicketSummary()));
        }
        if (trimToNull(request != null ? request.getTicketDescription() : null) != null) {
            ticket.setTicketDescription(trimToNull(request.getTicketDescription()));
        }
        ticket.setExternalStatus(externalStatus);
        ticket.setLastExternalUpdateAt(syncedAt);
        ticket.setLastExternalUpdatedBy(trimToNull(request != null ? request.getExternalUpdatedBy() : null));
        ticket.setLastExternalComment(trimToNull(request != null ? request.getStatusComment() : null));
        ticket.setLastSyncedAt(syncedAt);
        ticket.setDeliveryStatus("SYNCED");
        ticket.setErrorMessage(null);

        ExceptionExternalTicket savedTicket = externalTicketRepository.save(ticket);
        saveSyncEvent(
                savedTicket,
                channel,
                defaultIfBlank(trimToNull(request != null ? request.getEventType() : null), "EXTERNAL_STATUS_SYNC"),
                externalStatus,
                trimToNull(request != null ? request.getStatusComment() : null),
                trimToNull(request != null ? request.getExternalUpdatedBy() : null),
                serialize(request),
                syncedAt
        );

        if (savedTicket.getExceptionCase() != null) {
            syncCaseStatusFromExternal(savedTicket.getExceptionCase(), externalStatus, channel, syncedAt);
        }

        return toTicketDto(savedTicket);
    }

    private TicketDeliveryResult sendTicket(ExceptionIntegrationChannel channel,
                                            CollaborationContext context,
                                            String requestedSummary,
                                            String requestedDescription) {
        if (!TICKETING_TYPES.contains(normalize(channel.getChannelType()))) {
            throw new IllegalArgumentException("Selected channel does not support ticket creation");
        }
        if (!webhookEnabled) {
            throw new IllegalStateException("Webhook delivery is disabled for this environment");
        }

        String summary = requestedSummary != null ? requestedSummary : defaultTicketSummary(context);
        String description = requestedDescription != null ? requestedDescription : buildTicketDescription(channel, context);
        Map<String, Object> payload = buildTicketPayload(channel, context, summary, description);
        DeliveryResult delivery = postJson(channel, payload);
        if (!delivery.success()) {
            return new TicketDeliveryResult(summary, description, null, null, "FAILED", delivery.responseStatusCode(), delivery.requestPayload(), delivery.responsePayload(), delivery.errorMessage());
        }
        TicketReference reference = extractTicketReference(channel, delivery.responsePayload());
        return new TicketDeliveryResult(summary, description, reference.reference(), reference.url(), "CREATED", delivery.responseStatusCode(), delivery.requestPayload(), delivery.responsePayload(), null);
    }

    private CommunicationDeliveryResult sendCommunication(ExceptionIntegrationChannel channel,
                                                          CollaborationContext context,
                                                          String requestedSubject,
                                                          String requestedMessage) {
        if (!COMMUNICATION_TYPES.contains(normalize(channel.getChannelType()))) {
            throw new IllegalArgumentException("Selected channel does not support outbound communications");
        }
        String subject = requestedSubject != null ? requestedSubject : defaultCommunicationSubject(context);
        String message = requestedMessage != null ? requestedMessage : buildCommunicationBody(context);
        String channelType = normalize(channel.getChannelType());

        if ("EMAIL".equals(channelType)) {
            if (!emailEnabled) {
                throw new IllegalStateException("Email delivery is disabled for this environment");
            }
            String recipient = trimToNull(channel.getRecipientEmail());
            if (recipient == null) {
                throw new IllegalArgumentException("Recipient email is not configured for this channel");
            }
            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
                helper.setFrom(fromAddress, fromName);
                helper.setTo(recipient);
                helper.setSubject(subject);
                helper.setText(message, false);
                mailSender.send(mimeMessage);
                return new CommunicationDeliveryResult(recipient, subject, message, "SENT", null, null, null, null, LocalDateTime.now());
            } catch (Exception ex) {
                log.error("Exception communication email failed to {}: {}", recipient, ex.getMessage(), ex);
                return new CommunicationDeliveryResult(recipient, subject, message, "FAILED", null, null, null, truncate(ex.getMessage()), null);
            }
        }

        if (!webhookEnabled) {
            throw new IllegalStateException("Webhook delivery is disabled for this environment");
        }
        Object payload = buildCommunicationPayload(channel, context, subject, message);
        DeliveryResult delivery = postJson(channel, payload);
        return new CommunicationDeliveryResult(
                trimToNull(channel.getEndpointUrl()),
                subject,
                message,
                delivery.success() ? "SENT" : "FAILED",
                delivery.responseStatusCode(),
                delivery.requestPayload(),
                delivery.responsePayload(),
                delivery.errorMessage(),
                delivery.success() ? LocalDateTime.now() : null
        );
    }

    private Map<String, Object> buildTicketPayload(ExceptionIntegrationChannel channel,
                                                   CollaborationContext context,
                                                   String summary,
                                                   String description) {
        return switch (normalize(channel.getChannelType())) {
            case "SERVICENOW" -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("short_description", summary);
                payload.put("description", description);
                payload.put("u_retailinq_module", context.reconView());
                payload.put("u_store_id", context.storeId());
                payload.put("u_transaction_key", context.transactionKey());
                payload.put("u_incident_key", context.incidentKey());
                yield payload;
            }
            case "JIRA" -> Map.of(
                    "fields", Map.of(
                            "project", Map.of("key", Objects.requireNonNullElse(trimToNull(channel.getDefaultProjectKey()), "OPS")),
                            "summary", summary,
                            "description", description,
                            "issuetype", Map.of("name", Objects.requireNonNullElse(trimToNull(channel.getDefaultIssueType()), "Task")),
                            "labels", List.of("retailinq", context.reconView().toLowerCase(Locale.ROOT))
                    )
            );
            default -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("ticketSummary", summary);
                payload.put("ticketDescription", description);
                payload.put("module", context.reconView());
                payload.put("storeId", context.storeId());
                payload.put("transactionKey", context.transactionKey());
                payload.put("incidentKey", context.incidentKey());
                payload.put("contextTitle", context.contextTitle());
                payload.put("openInRetailInqUrl", appBaseUrl);
                if (channel.isInboundSyncEnabled()) {
                    payload.put("retailInqSync", Map.of(
                            "callbackUrl", buildCallbackUrl(channel),
                            "callbackSecretHeader", CALLBACK_SECRET_HEADER
                    ));
                }
                yield payload;
            }
        };
    }

    private Object buildCommunicationPayload(ExceptionIntegrationChannel channel,
                                             CollaborationContext context,
                                             String subject,
                                             String message) {
        return switch (normalize(channel.getChannelType())) {
            case "MICROSOFT_TEAMS" -> Map.of(
                    "@type", "MessageCard",
                    "@context", "https://schema.org/extensions",
                    "summary", "[RetailINQ] " + subject,
                    "themeColor", "1565C0",
                    "title", subject,
                    "sections", List.of(Map.of(
                            "activityTitle", context.contextTitle(),
                            "facts", List.of(
                                    Map.of("name", "Module", "value", context.reconView()),
                                    Map.of("name", "Store", "value", Objects.toString(context.storeId(), "-")),
                                    Map.of("name", "Transaction", "value", Objects.toString(context.transactionKey(), "-")),
                                    Map.of("name", "Incident", "value", Objects.toString(context.incidentKey(), "-"))
                            ),
                            "text", message,
                            "markdown", true
                    )),
                    "potentialAction", List.of(Map.of(
                            "@type", "OpenUri",
                            "name", "Open RetailINQ",
                            "targets", List.of(Map.of("os", "default", "uri", appBaseUrl))
                    ))
            );
            default -> Map.of(
                    "subject", subject,
                    "message", message,
                    "module", context.reconView(),
                    "storeId", context.storeId(),
                    "transactionKey", context.transactionKey(),
                    "incidentKey", context.incidentKey(),
                    "contextTitle", context.contextTitle(),
                    "openInRetailInqUrl", appBaseUrl
            );
        };
    }

    private void autoCreateCaseTicket(ExceptionCase exceptionCase,
                                      ExceptionIntegrationChannel channel,
                                      String actorUsername,
                                      String lifecycleEventType) {
        CollaborationContext context = contextFromCase(exceptionCase);
        String summary = buildLifecycleTicketSummary(context, exceptionCase, lifecycleEventType);
        String description = buildLifecycleTicketDescription(channel, context, exceptionCase, lifecycleEventType);
        TicketDeliveryResult result = sendTicket(channel, context, summary, description);
        externalTicketRepository.save(ExceptionExternalTicket.builder()
                .tenantId(exceptionCase.getTenantId())
                .exceptionCase(exceptionCase)
                .transactionKey(exceptionCase.getTransactionKey())
                .incidentKey(null)
                .incidentTitle(null)
                .reconView(exceptionCase.getReconView())
                .storeId(context.storeId())
                .channel(channel)
                .channelName(channel.getChannelName())
                .channelType(channel.getChannelType())
                .ticketSummary(result.ticketSummary())
                .ticketDescription(result.ticketDescription())
                .externalReference(result.externalReference())
                .externalUrl(result.externalUrl())
                .deliveryStatus(result.deliveryStatus())
                .externalStatus("OPEN")
                .responseStatusCode(result.responseStatusCode())
                .requestPayload(result.requestPayload())
                .responsePayload(result.responsePayload())
                .errorMessage(result.errorMessage())
                .createdBy(actorUsername)
                .lastSyncedAt(LocalDateTime.now())
                .build());
    }

    private void pushEscalationUpdate(ExceptionExternalTicket ticket,
                                      ExceptionCase exceptionCase,
                                      String actorUsername) {
        ExceptionIntegrationChannel channel = ticket.getChannel();
        if (channel == null) {
            return;
        }
        CollaborationContext context = contextFromCase(exceptionCase);
        Object payload = buildEscalationPayload(channel, context, exceptionCase, ticket);
        DeliveryResult delivery;
        String externalUrl = trimToNull(ticket.getExternalUrl());
        if ("GENERIC_WEBHOOK".equals(normalize(channel.getChannelType()))) {
            delivery = postJson(channel, payload);
        } else if (externalUrl != null) {
            HttpMethod method = "SERVICENOW".equals(normalize(channel.getChannelType())) ? HttpMethod.PATCH : HttpMethod.PUT;
            delivery = exchangeJson(method, externalUrl, channel.getHeadersJson(), payload, "External ticket escalation update");
        } else {
            ticket.setErrorMessage("Escalation sync skipped because the external ticket URL is unavailable");
            externalTicketRepository.save(ticket);
            return;
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        ticket.setLastSyncedAt(syncedAt);
        ticket.setDeliveryStatus(delivery.success() ? "UPDATED" : "SYNC_FAILED");
        ticket.setResponseStatusCode(delivery.responseStatusCode());
        ticket.setRequestPayload(delivery.requestPayload());
        ticket.setResponsePayload(delivery.responsePayload());
        ticket.setErrorMessage(delivery.errorMessage());
        externalTicketRepository.save(ticket);

        if (delivery.success()) {
            saveSyncEvent(
                    ticket,
                    channel,
                    "OUTBOUND_ESCALATION_PUSH",
                    ticket.getExternalStatus(),
                    escalationStatusNote(exceptionCase),
                    actorUsername,
                    delivery.requestPayload(),
                    syncedAt
            );
        }
    }

    private Object buildEscalationPayload(ExceptionIntegrationChannel channel,
                                          CollaborationContext context,
                                          ExceptionCase exceptionCase,
                                          ExceptionExternalTicket ticket) {
        String note = escalationStatusNote(exceptionCase);
        return switch (normalize(channel.getChannelType())) {
            case "SERVICENOW" -> Map.of(
                    "work_notes", note,
                    "short_description", buildLifecycleTicketSummary(context, exceptionCase, "CASE_ESCALATED")
            );
            case "JIRA" -> Map.of(
                    "fields", Map.of(
                            "summary", buildLifecycleTicketSummary(context, exceptionCase, "CASE_ESCALATED"),
                            "description", buildLifecycleTicketDescription(channel, context, exceptionCase, "CASE_ESCALATED")
                    )
            );
            default -> Map.of(
                    "eventType", "CASE_ESCALATED",
                    "externalReference", ticket.getExternalReference(),
                    "transactionKey", context.transactionKey(),
                    "reconView", context.reconView(),
                    "storeId", context.storeId(),
                    "severity", exceptionCase.getSeverity(),
                    "caseStatus", exceptionCase.getCaseStatus(),
                    "nextAction", exceptionCase.getNextAction(),
                    "note", note
            );
        };
    }

    private DeliveryResult postJson(ExceptionIntegrationChannel channel, Object payloadObject) {
        String endpointUrl = trimToNull(channel.getEndpointUrl());
        if (endpointUrl == null) {
            throw new IllegalArgumentException("Endpoint URL is not configured for this channel");
        }

        String requestPayload = serialize(payloadObject);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.ALL));
        parseHeadersJson(channel.getHeadersJson()).forEach(headers::set);

        try {
            ResponseEntity<String> response = restTemplate().postForEntity(
                    endpointUrl,
                    new HttpEntity<>(requestPayload, headers),
                    String.class
            );
            return new DeliveryResult(true, response.getStatusCode().value(), requestPayload, truncate(response.getBody()), null);
        } catch (RestClientException ex) {
            log.error("Exception collaboration delivery failed for channel {} to {}: {}", channel.getChannelName(), endpointUrl, ex.getMessage(), ex);
            return new DeliveryResult(false, null, requestPayload, null, truncate(ex.getMessage()));
        }
    }

    private DeliveryResult exchangeJson(HttpMethod method,
                                        String url,
                                        String headersJson,
                                        Object payloadObject,
                                        String actionLabel) {
        String requestPayload = serialize(payloadObject);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.ALL));
        parseHeadersJson(headersJson).forEach(headers::set);
        try {
            ResponseEntity<String> response = restTemplate().exchange(
                    url,
                    method,
                    new HttpEntity<>(requestPayload, headers),
                    String.class
            );
            return new DeliveryResult(true, response.getStatusCode().value(), requestPayload, truncate(response.getBody()), null);
        } catch (RestClientException ex) {
            log.error("{} failed against {}: {}", actionLabel, url, ex.getMessage(), ex);
            return new DeliveryResult(false, null, requestPayload, null, truncate(ex.getMessage()));
        }
    }

    private TicketReference extractTicketReference(ExceptionIntegrationChannel channel, String responsePayload) {
        if (responsePayload == null || responsePayload.isBlank()) {
            return new TicketReference(null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(responsePayload);
            if ("SERVICENOW".equals(normalize(channel.getChannelType()))) {
                String number = firstText(root, "result.number", "number", "result.sys_id", "sys_id");
                String link = firstText(root, "result.link", "link", "result.url", "url");
                return new TicketReference(number, link);
            }
            if ("JIRA".equals(normalize(channel.getChannelType()))) {
                String key = firstText(root, "key", "result.key", "id");
                String link = firstText(root, "self", "url", "result.self");
                return new TicketReference(key, link);
            }
            String genericReference = firstText(root, "ticketNumber", "number", "key", "id", "reference");
            String genericUrl = firstText(root, "url", "link", "self");
            return new TicketReference(genericReference, genericUrl);
        } catch (Exception ex) {
            return new TicketReference(null, null);
        }
    }

    private String firstText(JsonNode root, String... paths) {
        for (String path : paths) {
            JsonNode current = root;
            for (String segment : path.split("\\.")) {
                current = current != null ? current.get(segment) : null;
            }
            if (current != null && !current.isNull() && !current.asText().isBlank()) {
                return current.asText();
            }
        }
        return null;
    }

    private ExceptionExternalTicket resolveSyncTarget(ExceptionIntegrationChannel channel,
                                                      SyncExceptionExternalTicketRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Sync request is required");
        }
        String tenantId = channel.getTenantId();
        String externalReference = trimToNull(request.getExternalReference());
        if (externalReference != null) {
            return externalTicketRepository
                    .findTopByTenantIdAndChannelIdAndExternalReferenceOrderByCreatedAtDesc(tenantId, channel.getId(), externalReference)
                    .orElseThrow(() -> new IllegalArgumentException("External ticket reference not found"));
        }
        String transactionKey = trimToNull(request.getTransactionKey());
        if (transactionKey != null) {
            String reconView = normalize(trimToNull(request.getReconView()) != null ? request.getReconView() : channel.getReconView());
            if (reconView != null) {
                return externalTicketRepository
                        .findTopByTenantIdAndChannelIdAndTransactionKeyAndReconViewOrderByCreatedAtDesc(tenantId, channel.getId(), transactionKey, reconView)
                        .orElseGet(() -> externalTicketRepository
                                .findTopByTenantIdAndChannelIdAndTransactionKeyOrderByCreatedAtDesc(tenantId, channel.getId(), transactionKey)
                                .orElseThrow(() -> new IllegalArgumentException("Case ticket not found for transaction key")));
            }
            return externalTicketRepository
                    .findTopByTenantIdAndChannelIdAndTransactionKeyOrderByCreatedAtDesc(tenantId, channel.getId(), transactionKey)
                    .orElseThrow(() -> new IllegalArgumentException("Case ticket not found for transaction key"));
        }
        String incidentKey = trimToNull(request.getIncidentKey());
        if (incidentKey != null) {
            return externalTicketRepository
                    .findTopByTenantIdAndChannelIdAndIncidentKeyOrderByCreatedAtDesc(tenantId, channel.getId(), incidentKey)
                    .orElseThrow(() -> new IllegalArgumentException("Incident ticket not found for incident key"));
        }
        throw new IllegalArgumentException("Provide externalReference, transactionKey, or incidentKey for inbound sync");
    }

    private ExceptionIntegrationChannel resolveChannel(String tenantId,
                                                       UUID channelId,
                                                       String requiredGroup,
                                                       String reconView) {
        if (channelId == null) {
            throw new IllegalArgumentException("Integration channel is required");
        }
        ExceptionIntegrationChannel channel = channelRepository.findByIdAndTenantId(channelId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Integration channel not found"));
        if (!channel.isActive()) {
            throw new IllegalArgumentException("Selected integration channel is inactive");
        }
        String normalizedRequiredGroup = normalize(requiredGroup);
        String normalizedChannelGroup = normalize(channel.getChannelGroup());
        if (!Objects.equals("BOTH", normalizedChannelGroup) && !Objects.equals(normalizedRequiredGroup, normalizedChannelGroup)) {
            throw new IllegalArgumentException("Selected integration channel does not support this action");
        }
        String normalizedReconView = normalize(reconView);
        if (channel.getReconView() != null && !Objects.equals(channel.getReconView(), normalizedReconView)) {
            throw new IllegalArgumentException("Selected integration channel is not configured for module " + normalizedReconView);
        }
        return channel;
    }

    private void syncCaseStatusFromExternal(ExceptionCase exceptionCase,
                                            String externalStatus,
                                            ExceptionIntegrationChannel channel,
                                            LocalDateTime syncedAt) {
        String mappedStatus = mapCaseStatusFromExternal(externalStatus);
        if (mappedStatus == null || Objects.equals(mappedStatus, exceptionCase.getCaseStatus())) {
            return;
        }
        exceptionCase.setCaseStatus(mappedStatus);
        exceptionCase.setUpdatedBy("integration:" + channel.getChannelName());
        exceptionSlaService.applyRule(exceptionCase);
        caseRepository.save(exceptionCase);
        log.info("Case {} synchronized from external ticket status {} to case status {} at {}",
                exceptionCase.getTransactionKey(),
                externalStatus,
                mappedStatus,
                syncedAt);
    }

    private CollaborationContext contextFromCase(ExceptionCase exceptionCase) {
        String storeId = trimToNull(exceptionScopeResolver.resolveStoreId(exceptionCase));
        return new CollaborationContext(
                normalize(exceptionCase.getReconView()),
                storeId,
                exceptionCase.getTransactionKey(),
                null,
                "Case " + exceptionCase.getTransactionKey()
        );
    }

    private CollaborationContext contextFromIncident(String reconView,
                                                     CreateExceptionExternalTicketRequest request) {
        if (request == null || trimToNull(request.getIncidentKey()) == null) {
            throw new IllegalArgumentException("Incident key is required");
        }
        return new CollaborationContext(
                normalize(reconView),
                normalizeStoreId(request.getStoreId()),
                null,
                trimToNull(request.getIncidentKey()),
                trimToNull(request.getIncidentTitle()) != null ? request.getIncidentTitle() : request.getIncidentKey()
        );
    }

    private CollaborationContext contextFromIncident(String reconView,
                                                     SendExceptionCommunicationRequest request) {
        if (request == null || trimToNull(request.getIncidentKey()) == null) {
            throw new IllegalArgumentException("Incident key is required");
        }
        return new CollaborationContext(
                normalize(reconView),
                normalizeStoreId(request.getStoreId()),
                null,
                trimToNull(request.getIncidentKey()),
                trimToNull(request.getIncidentTitle()) != null ? request.getIncidentTitle() : request.getIncidentKey()
        );
    }

    private String defaultTicketSummary(CollaborationContext context) {
        return context.incidentKey() != null
                ? context.contextTitle() + " | " + context.reconView() + " | Store " + Objects.toString(context.storeId(), "-")
                : "RetailINQ case " + context.transactionKey() + " | " + context.reconView();
    }

    private String buildTicketDescription(ExceptionIntegrationChannel channel, CollaborationContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("Raised from RetailINQ ticketing integration.\n")
                .append("Module: ").append(context.reconView()).append('\n')
                .append("Store: ").append(Objects.toString(context.storeId(), "-")).append('\n');
        if (context.transactionKey() != null) {
            builder.append("Transaction Key: ").append(context.transactionKey()).append('\n');
        }
        if (context.incidentKey() != null) {
            builder.append("Incident Key: ").append(context.incidentKey()).append('\n');
            builder.append("Incident Title: ").append(context.contextTitle()).append('\n');
        }
        builder.append("Open in RetailINQ: ").append(appBaseUrl);
        if (channel != null && channel.isInboundSyncEnabled()) {
            builder.append("\nSync callback URL: ").append(buildCallbackUrl(channel))
                    .append("\nSync secret header: ").append(CALLBACK_SECRET_HEADER);
        }
        return builder.toString();
    }

    private String buildLifecycleTicketSummary(CollaborationContext context,
                                               ExceptionCase exceptionCase,
                                               String lifecycleEventType) {
        String prefix = "CASE_ESCALATED".equalsIgnoreCase(lifecycleEventType)
                ? "Escalated RetailINQ case"
                : "New RetailINQ case";
        return prefix + " " + context.transactionKey()
                + " | " + context.reconView()
                + " | Severity " + Objects.toString(exceptionCase.getSeverity(), "-");
    }

    private String buildLifecycleTicketDescription(ExceptionIntegrationChannel channel,
                                                   CollaborationContext context,
                                                   ExceptionCase exceptionCase,
                                                   String lifecycleEventType) {
        StringBuilder builder = new StringBuilder(buildTicketDescription(channel, context))
                .append("\nLifecycle Event: ").append(lifecycleEventType)
                .append("\nCase Status: ").append(Objects.toString(exceptionCase.getCaseStatus(), "-"))
                .append("\nSeverity: ").append(Objects.toString(exceptionCase.getSeverity(), "-"));
        if (trimToNull(exceptionCase.getReasonCode()) != null) {
            builder.append("\nReason Code: ").append(exceptionCase.getReasonCode());
        }
        if (trimToNull(exceptionCase.getNextAction()) != null) {
            builder.append("\nNext Action: ").append(exceptionCase.getNextAction());
        }
        return builder.toString();
    }

    private String escalationStatusNote(ExceptionCase exceptionCase) {
        StringBuilder builder = new StringBuilder("RetailINQ case escalation pushed from the exception workbench.")
                .append(" Severity: ").append(Objects.toString(exceptionCase.getSeverity(), "-"))
                .append(". Case Status: ").append(Objects.toString(exceptionCase.getCaseStatus(), "-"));
        if (trimToNull(exceptionCase.getNextAction()) != null) {
            builder.append(". Next Action: ").append(exceptionCase.getNextAction());
        }
        return builder.toString();
    }

    private String defaultCommunicationSubject(CollaborationContext context) {
        return context.incidentKey() != null
                ? "[RetailINQ] Store incident update: " + context.contextTitle()
                : "[RetailINQ] Case update: " + context.transactionKey();
    }

    private String buildCommunicationBody(CollaborationContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("RetailINQ exception update\n\n")
                .append("Module: ").append(context.reconView()).append('\n')
                .append("Store: ").append(Objects.toString(context.storeId(), "-")).append('\n');
        if (context.transactionKey() != null) {
            builder.append("Transaction Key: ").append(context.transactionKey()).append('\n');
        }
        if (context.incidentKey() != null) {
            builder.append("Incident Key: ").append(context.incidentKey()).append('\n');
            builder.append("Incident Title: ").append(context.contextTitle()).append('\n');
        }
        builder.append("\nOpen in RetailINQ: ").append(appBaseUrl);
        return builder.toString();
    }

    public ExceptionIntegrationChannelDto toChannelDto(ExceptionIntegrationChannel channel) {
        var tenant = tenantService.getTenant(channel.getTenantId());
        return ExceptionIntegrationChannelDto.builder()
                .id(channel.getId())
                .channelName(channel.getChannelName())
                .channelType(channel.getChannelType())
                .channelGroup(channel.getChannelGroup())
                .reconView(channel.getReconView())
                .endpointUrl(channel.getEndpointUrl())
                .recipientEmail(channel.getRecipientEmail())
                .headersJson(channel.getHeadersJson())
                .defaultProjectKey(channel.getDefaultProjectKey())
                .defaultIssueType(channel.getDefaultIssueType())
                .description(channel.getDescription())
                .active(channel.isActive())
                .inboundSyncEnabled(channel.isInboundSyncEnabled())
                .inboundSharedSecretConfigured(trimToNull(channel.getInboundSharedSecret()) != null)
                .autoCreateOnCaseOpen(channel.isAutoCreateOnCaseOpen())
                .autoCreateOnEscalation(channel.isAutoCreateOnEscalation())
                .callbackUrl(buildCallbackUrl(channel))
                .createdBy(channel.getCreatedBy())
                .updatedBy(channel.getUpdatedBy())
                .createdAt(TimezoneConverter.toDisplay(stringValue(channel.getCreatedAt()), tenant))
                .updatedAt(TimezoneConverter.toDisplay(stringValue(channel.getUpdatedAt()), tenant))
                .build();
    }

    public ExceptionExternalTicketDto toTicketDto(ExceptionExternalTicket ticket) {
        var tenant = tenantService.getTenant(ticket.getTenantId());
        return ExceptionExternalTicketDto.builder()
                .id(ticket.getId())
                .channelName(ticket.getChannelName())
                .channelType(ticket.getChannelType())
                .ticketSummary(ticket.getTicketSummary())
                .ticketDescription(ticket.getTicketDescription())
                .externalReference(ticket.getExternalReference())
                .externalUrl(ticket.getExternalUrl())
                .deliveryStatus(ticket.getDeliveryStatus())
                .externalStatus(ticket.getExternalStatus())
                .responseStatusCode(ticket.getResponseStatusCode())
                .errorMessage(ticket.getErrorMessage())
                .createdBy(ticket.getCreatedBy())
                .createdAt(TimezoneConverter.toDisplay(stringValue(ticket.getCreatedAt()), tenant))
                .lastSyncedAt(TimezoneConverter.toDisplay(stringValue(ticket.getLastSyncedAt()), tenant))
                .lastExternalUpdateAt(TimezoneConverter.toDisplay(stringValue(ticket.getLastExternalUpdateAt()), tenant))
                .lastExternalUpdatedBy(ticket.getLastExternalUpdatedBy())
                .lastExternalComment(ticket.getLastExternalComment())
                .build();
    }

    public ExceptionOutboundCommunicationDto toCommunicationDto(ExceptionOutboundCommunication record) {
        var tenant = tenantService.getTenant(record.getTenantId());
        return ExceptionOutboundCommunicationDto.builder()
                .id(record.getId())
                .channelName(record.getChannelName())
                .channelType(record.getChannelType())
                .recipient(record.getRecipient())
                .subject(record.getSubject())
                .messageBody(record.getMessageBody())
                .deliveryStatus(record.getDeliveryStatus())
                .responseStatusCode(record.getResponseStatusCode())
                .errorMessage(record.getErrorMessage())
                .createdBy(record.getCreatedBy())
                .createdAt(TimezoneConverter.toDisplay(stringValue(record.getCreatedAt()), tenant))
                .deliveredAt(TimezoneConverter.toDisplay(stringValue(record.getDeliveredAt()), tenant))
                .build();
    }

    private Predicate<ExceptionIntegrationChannel> matchesReconView(String normalizedReconView) {
        if (normalizedReconView == null) {
            return channel -> true;
        }
        return channel -> channel.getReconView() == null || Objects.equals(channel.getReconView(), normalizedReconView);
    }

    private boolean isFailed(ExceptionExternalTicket ticket) {
        return "FAILED".equalsIgnoreCase(ticket.getDeliveryStatus());
    }

    private boolean isFailed(ExceptionOutboundCommunication record) {
        return "FAILED".equalsIgnoreCase(record.getDeliveryStatus());
    }

    private Map<String, String> parseHeadersJson(String headersJson) {
        String raw = trimToNull(headersJson);
        if (raw == null) {
            return Map.of();
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            Map<String, String> headers = new LinkedHashMap<>();
            root.fields().forEachRemaining(entry -> headers.put(entry.getKey(), entry.getValue().asText()));
            return headers;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Headers JSON must be a valid object of header/value pairs");
        }
    }

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }

    private String serialize(Object payloadObject) {
        try {
            return objectMapper.writeValueAsString(payloadObject);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbound payload", ex);
        }
    }

    private String normalize(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String normalizeExternalStatus(String value) {
        return normalize(value);
    }

    private String normalizeStoreId(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.length() <= 4000 ? value : value.substring(0, 4000);
    }

    private boolean isEscalatedCase(String previousSeverity,
                                    String previousCaseStatus,
                                    ExceptionCase exceptionCase) {
        if (exceptionCase == null) {
            return false;
        }
        int previousSeverityRank = severityRank(previousSeverity);
        int currentSeverityRank = severityRank(exceptionCase.getSeverity());
        if (currentSeverityRank > previousSeverityRank && currentSeverityRank >= severityRank("HIGH")) {
            return true;
        }
        return !Objects.equals(normalize(previousCaseStatus), normalize(exceptionCase.getCaseStatus()))
                && "PENDING_APPROVAL".equals(normalize(exceptionCase.getCaseStatus()));
    }

    private int severityRank(String severity) {
        return switch (normalize(severity)) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private String mapCaseStatusFromExternal(String externalStatus) {
        String normalized = normalizeExternalStatus(externalStatus);
        if (normalized == null) {
            return null;
        }
        if (normalized.contains("RESOLVED") || normalized.contains("DONE") || normalized.contains("CLOSED") || normalized.contains("COMPLETED")) {
            return "RESOLVED";
        }
        if (normalized.contains("IGNORE") || normalized.contains("WONTFIX") || normalized.contains("WON'T FIX") || normalized.contains("REJECTED")) {
            return "IGNORED";
        }
        if (normalized.contains("PROGRESS") || normalized.contains("PENDING") || normalized.contains("ASSIGNED") || normalized.contains("WORK")) {
            return "IN_REVIEW";
        }
        if (normalized.contains("OPEN") || normalized.contains("NEW") || normalized.contains("TODO") || normalized.contains("BACKLOG")) {
            return "OPEN";
        }
        return null;
    }

    private void saveSyncEvent(ExceptionExternalTicket ticket,
                               ExceptionIntegrationChannel channel,
                               String eventType,
                               String externalStatus,
                               String statusNote,
                               String externalUpdatedBy,
                               String payload,
                               LocalDateTime syncedAt) {
        syncEventRepository.save(ExceptionExternalTicketSyncEvent.builder()
                .ticket(ticket)
                .tenantId(ticket.getTenantId())
                .channel(channel)
                .exceptionCase(ticket.getExceptionCase())
                .transactionKey(ticket.getTransactionKey())
                .incidentKey(ticket.getIncidentKey())
                .reconView(ticket.getReconView())
                .storeId(ticket.getStoreId())
                .externalReference(ticket.getExternalReference())
                .eventType(defaultIfBlank(eventType, "EXTERNAL_STATUS_SYNC"))
                .externalStatus(externalStatus)
                .statusNote(statusNote)
                .externalUpdatedBy(externalUpdatedBy)
                .payload(truncate(payload))
                .syncedAt(syncedAt)
                .build());
    }

    private String buildCallbackUrl(ExceptionIntegrationChannel channel) {
        return trimTrailingSlash(callbackBaseUrl) + "/api/v1/exceptions/integration-callbacks/" + channel.getId();
    }

    private String trimTrailingSlash(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return "";
        }
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed != null ? trimmed : fallback;
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private record CollaborationContext(String reconView,
                                        String storeId,
                                        String transactionKey,
                                        String incidentKey,
                                        String contextTitle) {
    }

    private record DeliveryResult(boolean success,
                                  Integer responseStatusCode,
                                  String requestPayload,
                                  String responsePayload,
                                  String errorMessage) {
    }

    private record TicketReference(String reference, String url) {
    }

    private record TicketDeliveryResult(String ticketSummary,
                                        String ticketDescription,
                                        String externalReference,
                                        String externalUrl,
                                        String deliveryStatus,
                                        Integer responseStatusCode,
                                        String requestPayload,
                                        String responsePayload,
                                        String errorMessage) {
    }

    private record CommunicationDeliveryResult(String recipient,
                                               String subject,
                                               String messageBody,
                                               String deliveryStatus,
                                               Integer responseStatusCode,
                                               String requestPayload,
                                               String responsePayload,
                                               String errorMessage,
                                               LocalDateTime deliveredAt) {
    }
}
