package com.recon.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.api.domain.ReconJobDefinition;
import com.recon.api.domain.ReconJobNotificationDelivery;
import com.recon.api.domain.ReconJobRun;
import com.recon.api.repository.ReconJobNotificationDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconJobNotificationService {

    private final ReconJobNotificationDeliveryRepository deliveryRepository;
    private final AlertEmailNotificationService alertEmailNotificationService;
    private final AlertWebhookNotificationService alertWebhookNotificationService;
    private final AuditLedgerService auditLedgerService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void notifyRunCompletion(ReconJobDefinition definition,
                                    ReconJobRun run,
                                    Map<String, Object> resultPayload) {
        if (definition == null || run == null) {
            return;
        }
        String normalizedStatus = defaultIfBlank(run.getRunStatus(), "UNKNOWN").toUpperCase(Locale.ROOT);
        boolean shouldNotify = ("SUCCEEDED".equals(normalizedStatus) && definition.isNotifyOnSuccess())
                || (!"SUCCEEDED".equals(normalizedStatus) && definition.isNotifyOnFailure());
        if (!shouldNotify) {
            return;
        }

        String channelType = defaultIfBlank(definition.getNotificationChannelType(), "");
        if (channelType.isBlank()) {
            saveDelivery(definition, run, "NONE", null, "SKIPPED", null, "Notification channel is not configured", resultPayload);
            return;
        }

        String normalizedChannel = channelType.toUpperCase(Locale.ROOT);
        switch (normalizedChannel) {
            case "EMAIL" -> sendEmail(definition, run, resultPayload);
            case "GENERIC_WEBHOOK", "MICROSOFT_TEAMS", "SLACK" -> sendWebhook(definition, run, normalizedChannel, resultPayload);
            default -> saveDelivery(definition, run, normalizedChannel, null, "SKIPPED", null,
                    "Unsupported notification channel: " + channelType, resultPayload);
        }
    }

    private void sendEmail(ReconJobDefinition definition,
                           ReconJobRun run,
                           Map<String, Object> resultPayload) {
        String recipientEmail = trimToNull(definition.getNotificationEmail());
        if (recipientEmail == null) {
            saveDelivery(definition, run, "EMAIL", null, "SKIPPED", null, "Notification email is not configured", resultPayload);
            return;
        }
        String subject = "[RetailINQ Job] %s %s".formatted(run.getJobName(), run.getRunStatus());
        String body = buildEmailBody(run, resultPayload);
        boolean sent = alertEmailNotificationService.sendDirectEmail(
                definition.getTenantId(),
                definition.getReconView(),
                run.getId(),
                recipientEmail,
                subject,
                body
        );
        saveDelivery(definition, run, "EMAIL", recipientEmail, sent ? "SENT" : "FAILED", null,
                sent ? null : "Email delivery failed", resultPayload);
    }

    private void sendWebhook(ReconJobDefinition definition,
                             ReconJobRun run,
                             String channelType,
                             Map<String, Object> resultPayload) {
        String endpoint = trimToNull(definition.getNotificationEndpoint());
        if (endpoint == null) {
            saveDelivery(definition, run, channelType, null, "SKIPPED", null, "Notification endpoint is not configured", resultPayload);
            return;
        }
        Object payload = switch (channelType) {
            case "MICROSOFT_TEAMS" -> buildTeamsPayload(run, resultPayload);
            case "SLACK" -> buildSlackPayload(run, resultPayload);
            default -> buildGenericPayload(run, resultPayload);
        };
        boolean sent = alertWebhookNotificationService.sendDirectWebhook(
                definition.getTenantId(),
                definition.getReconView(),
                run.getId(),
                channelType,
                endpoint,
                payload
        );
        saveDelivery(definition, run, channelType, endpoint, sent ? "SENT" : "FAILED", null,
                sent ? null : "Webhook delivery failed", resultPayload);
    }

    private String buildEmailBody(ReconJobRun run, Map<String, Object> resultPayload) {
        StringBuilder body = new StringBuilder();
        body.append("RetailINQ reconciliation job completed.")
                .append("\n\n")
                .append("Job: ").append(defaultIfBlank(run.getJobName(), "Unnamed job")).append('\n')
                .append("Module: ").append(defaultIfBlank(run.getReconView(), "UNKNOWN")).append('\n')
                .append("Status: ").append(defaultIfBlank(run.getRunStatus(), "UNKNOWN")).append('\n')
                .append("Trigger: ").append(defaultIfBlank(run.getTriggerType(), "UNKNOWN")).append('\n')
                .append("Attempt: ").append(run.getAttemptNumber()).append('\n');
        if (trimToNull(run.getBusinessDate()) != null) {
            body.append("Business Date: ").append(run.getBusinessDate()).append('\n');
        }
        if (trimToNull(run.getSummary()) != null) {
            body.append("Summary: ").append(run.getSummary()).append('\n');
        }
        if (resultPayload != null && !resultPayload.isEmpty()) {
            body.append("\nPayload:\n").append(writeJson(resultPayload));
        }
        return body.toString();
    }

    private Map<String, Object> buildGenericPayload(ReconJobRun run,
                                                    Map<String, Object> resultPayload) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationType", "RECON_JOB_COMPLETION");
        payload.put("jobRunId", run.getId());
        payload.put("jobName", run.getJobName());
        payload.put("reconView", run.getReconView());
        payload.put("status", run.getRunStatus());
        payload.put("triggerType", run.getTriggerType());
        payload.put("attemptNumber", run.getAttemptNumber());
        payload.put("businessDate", run.getBusinessDate());
        payload.put("summary", run.getSummary());
        payload.put("startedAt", valueOrNull(run.getStartedAt()));
        payload.put("completedAt", valueOrNull(run.getCompletedAt()));
        payload.put("retryPending", run.isRetryPending());
        payload.put("result", resultPayload);
        return payload;
    }

    private Map<String, Object> buildTeamsPayload(ReconJobRun run,
                                                  Map<String, Object> resultPayload) {
        return Map.of(
                "@type", "MessageCard",
                "@context", "https://schema.org/extensions",
                "summary", "[RetailINQ Job] " + defaultIfBlank(run.getJobName(), "Reconciliation job"),
                "themeColor", "SUCCEEDED".equalsIgnoreCase(run.getRunStatus()) ? "2E7D32" : "C62828",
                "title", "%s - %s".formatted(defaultIfBlank(run.getJobName(), "Reconciliation job"), defaultIfBlank(run.getRunStatus(), "UNKNOWN")),
                "sections", List.of(Map.of(
                        "activityTitle", defaultIfBlank(run.getSummary(), "Job execution complete"),
                        "facts", List.of(
                                Map.of("name", "Module", "value", defaultIfBlank(run.getReconView(), "UNKNOWN")),
                                Map.of("name", "Trigger", "value", defaultIfBlank(run.getTriggerType(), "UNKNOWN")),
                                Map.of("name", "Attempt", "value", String.valueOf(run.getAttemptNumber())),
                                Map.of("name", "Business Date", "value", defaultIfBlank(run.getBusinessDate(), "-")),
                                Map.of("name", "Retry Pending", "value", String.valueOf(run.isRetryPending()))
                        ),
                        "text", resultPayload == null ? "" : writeJson(resultPayload)
                ))
        );
    }

    private Map<String, Object> buildSlackPayload(ReconJobRun run,
                                                  Map<String, Object> resultPayload) {
        return Map.of(
                "text", "[RetailINQ Job] " + defaultIfBlank(run.getJobName(), "Reconciliation job"),
                "blocks", List.of(
                        Map.of(
                                "type", "header",
                                "text", Map.of("type", "plain_text", "text",
                                        "%s - %s".formatted(defaultIfBlank(run.getJobName(), "Reconciliation job"), defaultIfBlank(run.getRunStatus(), "UNKNOWN")))
                        ),
                        Map.of(
                                "type", "section",
                                "fields", List.of(
                                        slackField("Module", defaultIfBlank(run.getReconView(), "UNKNOWN")),
                                        slackField("Trigger", defaultIfBlank(run.getTriggerType(), "UNKNOWN")),
                                        slackField("Attempt", String.valueOf(run.getAttemptNumber())),
                                        slackField("Business Date", defaultIfBlank(run.getBusinessDate(), "-"))
                                )
                        ),
                        Map.of(
                                "type", "section",
                                "text", Map.of("type", "mrkdwn", "text",
                                        defaultIfBlank(run.getSummary(), "Job execution complete")
                                                + (resultPayload == null ? "" : "\n```" + writeJson(resultPayload) + "```"))
                        )
                )
        );
    }

    private Map<String, Object> slackField(String title, String value) {
        return Map.of("type", "mrkdwn", "text", "*%s*\n%s".formatted(title, value));
    }

    private void saveDelivery(ReconJobDefinition definition,
                              ReconJobRun run,
                              String channelType,
                              String destination,
                              String status,
                              Integer responseCode,
                              String errorMessage,
                              Map<String, Object> payload) {
        ReconJobNotificationDelivery delivery = deliveryRepository.save(ReconJobNotificationDelivery.builder()
                .tenantId(definition.getTenantId())
                .jobRunId(run.getId())
                .channelType(defaultIfBlank(channelType, "NONE"))
                .destination(destination)
                .deliveryStatus(defaultIfBlank(status, "RECORDED"))
                .responseCode(responseCode)
                .errorMessage(errorMessage)
                .payloadJson(writeJson(payload))
                .build());

        Map<String, Object> auditState = new LinkedHashMap<>();
        auditState.put("deliveryId", delivery.getId());
        auditState.put("jobRunId", run.getId());
        auditState.put("channelType", defaultIfBlank(channelType, "NONE"));
        auditState.put("destination", destination);
        auditState.put("status", defaultIfBlank(status, "RECORDED"));
        auditState.put("errorMessage", errorMessage);
        auditState.put("createdAt", valueOrNull(delivery.getCreatedAt()));
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(definition.getTenantId())
                .sourceType("OPERATIONS")
                .moduleKey(defaultIfBlank(definition.getReconView(), "OPERATIONS"))
                .entityType("RECON_JOB_NOTIFICATION")
                .entityKey(delivery.getId().toString())
                .actionType("JOB_NOTIFICATION_" + defaultIfBlank(status, "RECORDED"))
                .title("Reconciliation job notification " + defaultIfBlank(status, "recorded").toLowerCase(Locale.ROOT))
                .summary("%s -> %s".formatted(defaultIfBlank(channelType, "NONE"), defaultIfBlank(destination, "unconfigured")))
                .actor("system")
                .status(status)
                .referenceKey(run.getId().toString())
                .controlFamily("OPERATIONS")
                .evidenceTags(List.of("RECON_JOB", "NOTIFICATION"))
                .afterState(auditState)
                .eventAt(LocalDateTime.now())
                .build());
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return Objects.toString(value, null);
        }
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String valueOrNull(Object value) {
        return value == null ? null : value.toString();
    }
}
