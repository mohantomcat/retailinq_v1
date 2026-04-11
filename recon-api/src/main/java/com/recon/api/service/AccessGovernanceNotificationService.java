package com.recon.api.service;

import com.recon.api.domain.TenantAuthConfigEntity;
import com.recon.api.domain.User;
import com.recon.api.repository.TenantAuthConfigRepository;
import com.recon.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccessGovernanceNotificationService {

    private static final String SECURITY_RECON_VIEW = "SECURITY";
    private static final String PENDING_MANAGER = "PENDING_MANAGER";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final TenantAuthConfigRepository tenantAuthConfigRepository;
    private final UserRepository userRepository;
    private final AlertEmailNotificationService alertEmailNotificationService;
    private final AlertWebhookNotificationService alertWebhookNotificationService;

    @Value("${app.alerting.email.app-base-url:http://localhost:5173}")
    private String appBaseUrl;

    @Scheduled(cron = "${app.security.access-review.reminder-cron:0 0 8 * * *}")
    public void runScheduledManagerAccessReviewReminders() {
        for (TenantAuthConfigEntity config : tenantAuthConfigRepository.findAll()) {
            try {
                dispatchManagerAccessReviewReminders(config, false);
            } catch (Exception ex) {
                log.error("Manager access review reminder dispatch failed for tenant {}: {}",
                        config.getTenantId(),
                        ex.getMessage(),
                        ex);
            }
        }
    }

    @Transactional
    public ReminderDispatchResult dispatchManagerAccessReviewRemindersForTenant(String tenantId, boolean ignoreCadence) {
        TenantAuthConfigEntity config = tenantAuthConfigRepository.findById(tenantId).orElse(null);
        if (config == null) {
            return new ReminderDispatchResult(0, 0, 0, 0);
        }
        return dispatchManagerAccessReviewReminders(config, ignoreCadence);
    }

    public void notifyPrivilegedAction(String tenantId,
                                       String actionType,
                                       String title,
                                       String summary,
                                       String actor,
                                       String entityType,
                                       String entityKey,
                                       String status,
                                       Object afterState,
                                       Map<String, Object> metadata) {
        TenantAuthConfigEntity config = tenantAuthConfigRepository.findById(tenantId).orElse(null);
        if (config == null || !config.isPrivilegedActionAlertsEnabled()) {
            return;
        }

        String subject = "[RetailINQ] Privileged access alert: " + defaultIfBlank(title, "Security event");
        String body = buildPrivilegedActionEmailBody(actionType, title, summary, actor, entityType, entityKey, status, metadata);
        UUID eventId = UUID.randomUUID();

        for (String recipientEmail : splitCsv(config.getPrivilegedActionAlertEmailRecipients())) {
            alertEmailNotificationService.sendDirectEmail(
                    tenantId,
                    SECURITY_RECON_VIEW,
                    eventId,
                    recipientEmail,
                    subject,
                    body);
        }

        sendWebhook(
                tenantId,
                eventId,
                "MICROSOFT_TEAMS",
                config.getPrivilegedActionAlertTeamsWebhookUrl(),
                buildPrivilegedActionWebhookPayload("MICROSOFT_TEAMS", actionType, title, summary, actor, entityType, entityKey, status, metadata));
        sendWebhook(
                tenantId,
                eventId,
                "SLACK",
                config.getPrivilegedActionAlertSlackWebhookUrl(),
                buildPrivilegedActionWebhookPayload("SLACK", actionType, title, summary, actor, entityType, entityKey, status, metadata));
    }

    @Transactional
    ReminderDispatchResult dispatchManagerAccessReviewReminders(TenantAuthConfigEntity config, boolean ignoreCadence) {
        if (config == null || !config.isManagerAccessReviewRemindersEnabled()) {
            return new ReminderDispatchResult(0, 0, 0, 0);
        }

        LocalDateTime now = LocalDateTime.now();
        int reminderIntervalDays = Math.max(1, config.getManagerAccessReviewReminderIntervalDays());
        List<User> tenantUsers = userRepository.findByTenantId(config.getTenantId());
        Map<UUID, User> usersById = tenantUsers.stream()
                .filter(user -> user.getId() != null)
                .collect(Collectors.toMap(
                        User::getId,
                        user -> user,
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<User> pendingUsers = tenantUsers.stream()
                .filter(User::isActive)
                .filter(user -> PENDING_MANAGER.equalsIgnoreCase(defaultIfBlank(user.getAccessReviewStatus(), "")))
                .filter(user -> ignoreCadence || reminderDue(user, now, reminderIntervalDays))
                .toList();

        if (pendingUsers.isEmpty()) {
            return new ReminderDispatchResult(0, 0, 0, 0);
        }

        Map<User, List<User>> pendingByManager = new LinkedHashMap<>();
        for (User pendingUser : pendingUsers) {
            User manager = pendingUser.getManagerUserId() == null ? null : usersById.get(pendingUser.getManagerUserId());
            if (manager == null || !manager.isActive()) {
                continue;
            }
            pendingByManager.computeIfAbsent(manager, ignored -> new ArrayList<>()).add(pendingUser);
        }

        Set<UUID> remindedUserIds = new LinkedHashSet<>();
        int managerEmailsSent = 0;
        for (Map.Entry<User, List<User>> entry : pendingByManager.entrySet()) {
            User manager = entry.getKey();
            String managerEmail = trimToNull(manager.getEmail());
            if (managerEmail == null) {
                continue;
            }
            UUID eventId = UUID.randomUUID();
            boolean sent = alertEmailNotificationService.sendDirectEmail(
                    config.getTenantId(),
                    SECURITY_RECON_VIEW,
                    eventId,
                    managerEmail,
                    buildManagerReminderSubject(entry.getValue().size()),
                    buildManagerReminderBody(manager, entry.getValue()));
            if (sent) {
                remindedUserIds.addAll(entry.getValue().stream()
                        .map(User::getId)
                        .filter(Objects::nonNull)
                        .toList());
                managerEmailsSent++;
            }
        }

        int broadcastDeliveriesSent = 0;
        UUID summaryEventId = UUID.randomUUID();
        String summarySubject = buildManagerSummarySubject(pendingUsers.size());
        String summaryBody = buildManagerSummaryBody(pendingUsers, usersById);
        boolean broadcastDelivered = false;

        for (String recipientEmail : splitCsv(config.getManagerAccessReviewAdditionalEmails())) {
            boolean sent = alertEmailNotificationService.sendDirectEmail(
                    config.getTenantId(),
                    SECURITY_RECON_VIEW,
                    summaryEventId,
                    recipientEmail,
                    summarySubject,
                    summaryBody);
            if (sent) {
                broadcastDeliveriesSent++;
                broadcastDelivered = true;
            }
        }

        if (sendWebhook(
                config.getTenantId(),
                summaryEventId,
                "MICROSOFT_TEAMS",
                config.getManagerAccessReviewTeamsWebhookUrl(),
                buildManagerReminderWebhookPayload("MICROSOFT_TEAMS", pendingUsers, usersById, reminderIntervalDays))) {
            broadcastDeliveriesSent++;
            broadcastDelivered = true;
        }
        if (sendWebhook(
                config.getTenantId(),
                summaryEventId,
                "SLACK",
                config.getManagerAccessReviewSlackWebhookUrl(),
                buildManagerReminderWebhookPayload("SLACK", pendingUsers, usersById, reminderIntervalDays))) {
            broadcastDeliveriesSent++;
            broadcastDelivered = true;
        }

        if (broadcastDelivered) {
            remindedUserIds.addAll(pendingUsers.stream()
                    .map(User::getId)
                    .filter(Objects::nonNull)
                    .toList());
        }

        if (!remindedUserIds.isEmpty()) {
            for (User pendingUser : pendingUsers) {
                if (remindedUserIds.contains(pendingUser.getId())) {
                    pendingUser.setAccessReviewLastReminderAt(now);
                    userRepository.save(pendingUser);
                }
            }
        }

        return new ReminderDispatchResult(
                pendingUsers.size(),
                remindedUserIds.size(),
                managerEmailsSent,
                broadcastDeliveriesSent);
    }

    private boolean reminderDue(User user, LocalDateTime now, int reminderIntervalDays) {
        LocalDateTime lastReminderAt = user.getAccessReviewLastReminderAt();
        return lastReminderAt == null || !lastReminderAt.isAfter(now.minusDays(reminderIntervalDays));
    }

    private boolean sendWebhook(String tenantId,
                                UUID eventId,
                                String channelType,
                                String endpointUrl,
                                Object payload) {
        String trimmedUrl = trimToNull(endpointUrl);
        if (trimmedUrl == null) {
            return false;
        }
        return alertWebhookNotificationService.sendDirectWebhook(
                tenantId,
                SECURITY_RECON_VIEW,
                eventId,
                channelType,
                trimmedUrl,
                payload);
    }

    private String buildManagerReminderSubject(int pendingUsers) {
        return "[RetailINQ] Manager access review reminder: %d pending user%s".formatted(
                pendingUsers,
                pendingUsers == 1 ? "" : "s");
    }

    private String buildManagerSummarySubject(int pendingUsers) {
        return "[RetailINQ] Access review summary: %d pending user%s".formatted(
                pendingUsers,
                pendingUsers == 1 ? "" : "s");
    }

    private String buildManagerReminderBody(User manager, List<User> pendingUsers) {
        StringBuilder body = new StringBuilder();
        body.append("Quarterly access review action is pending for your team.")
                .append("\n\n")
                .append("Manager: ")
                .append(defaultIfBlank(firstNonBlank(manager.getFullName(), manager.getUsername()), "Manager"))
                .append('\n')
                .append("Pending users: ")
                .append(pendingUsers.size())
                .append('\n')
                .append("Open the Dashboard and switch to Security > Tenant Access Center.")
                .append('\n')
                .append("Dashboard URL: ")
                .append(appBaseUrl)
                .append("\n\n")
                .append("Pending reviews:")
                .append('\n');
        appendUserLines(body, pendingUsers);
        return body.toString();
    }

    private String buildManagerSummaryBody(List<User> pendingUsers, Map<UUID, User> usersById) {
        long usersMissingManager = pendingUsers.stream()
                .filter(user -> user.getManagerUserId() == null || usersById.get(user.getManagerUserId()) == null)
                .count();
        StringBuilder body = new StringBuilder();
        body.append("Quarterly access review follow-up is pending.")
                .append("\n\n")
                .append("Pending users: ")
                .append(pendingUsers.size())
                .append('\n')
                .append("Users without manager assignment: ")
                .append(usersMissingManager)
                .append('\n')
                .append("Open the Dashboard and switch to Security > Tenant Access Center.")
                .append('\n')
                .append("Dashboard URL: ")
                .append(appBaseUrl)
                .append("\n\n")
                .append("Pending reviews:")
                .append('\n');
        appendUserLines(body, pendingUsers);
        return body.toString();
    }

    private void appendUserLines(StringBuilder body, List<User> users) {
        for (User user : users) {
            body.append("- ")
                    .append(firstNonBlank(user.getFullName(), user.getUsername(), "User"))
                    .append(" (")
                    .append(defaultIfBlank(user.getUsername(), "unknown"))
                    .append(")");
            if (user.getAccessReviewDueAt() != null) {
                body.append(" | Due ").append(DATE_FORMATTER.format(user.getAccessReviewDueAt()));
            }
            if (user.getRoles() != null && !user.getRoles().isEmpty()) {
                body.append(" | Roles: ")
                        .append(user.getRoles().stream()
                                .map(role -> role.getName())
                                .filter(Objects::nonNull)
                                .sorted()
                                .collect(Collectors.joining(", ")));
            }
            body.append('\n');
        }
    }

    private Object buildManagerReminderWebhookPayload(String channelType,
                                                     List<User> pendingUsers,
                                                     Map<UUID, User> usersById,
                                                     int reminderIntervalDays) {
        long missingManagers = pendingUsers.stream()
                .filter(user -> user.getManagerUserId() == null || usersById.get(user.getManagerUserId()) == null)
                .count();
        List<String> pendingLines = pendingUsers.stream()
                .limit(10)
                .map(user -> "- %s (%s)%s".formatted(
                        firstNonBlank(user.getFullName(), user.getUsername(), "User"),
                        defaultIfBlank(user.getUsername(), "unknown"),
                        user.getAccessReviewDueAt() == null ? "" : " due " + DATE_FORMATTER.format(user.getAccessReviewDueAt())))
                .toList();
        return switch (defaultIfBlank(channelType, "SLACK").toUpperCase(Locale.ROOT)) {
            case "MICROSOFT_TEAMS" -> Map.of(
                    "@type", "MessageCard",
                    "@context", "https://schema.org/extensions",
                    "summary", "[RetailINQ] Manager access review reminder",
                    "themeColor", "EF6C00",
                    "title", "Manager access review reminder",
                    "sections", List.of(Map.of(
                            "activityTitle", "%d pending user review%s".formatted(
                                    pendingUsers.size(),
                                    pendingUsers.size() == 1 ? "" : "s"),
                            "facts", List.of(
                                    Map.of("name", "Reminder interval", "value", reminderIntervalDays + " day(s)"),
                                    Map.of("name", "Users missing manager", "value", String.valueOf(missingManagers)),
                                    Map.of("name", "Dashboard", "value", appBaseUrl)
                            ),
                            "text", String.join("\n", pendingLines),
                            "markdown", true
                    )),
                    "potentialAction", List.of(Map.of(
                            "@type", "OpenUri",
                            "name", "Open Dashboard",
                            "targets", List.of(Map.of("os", "default", "uri", appBaseUrl))
                    )));
            default -> Map.of(
                    "text", "Manager access review reminder",
                    "blocks", List.of(
                            Map.of(
                                    "type", "header",
                                    "text", Map.of("type", "plain_text", "text", "Manager access review reminder")
                            ),
                            Map.of(
                                    "type", "section",
                                    "fields", List.of(
                                            slackField("Pending users", String.valueOf(pendingUsers.size())),
                                            slackField("Users missing manager", String.valueOf(missingManagers)),
                                            slackField("Reminder interval", reminderIntervalDays + " day(s)"),
                                            slackField("Dashboard", appBaseUrl)
                                    )
                            ),
                            Map.of(
                                    "type", "section",
                                    "text", Map.of("type", "mrkdwn", "text", String.join("\n", pendingLines))
                            ),
                            Map.of(
                                    "type", "actions",
                                    "elements", List.of(Map.of(
                                            "type", "button",
                                            "text", Map.of("type", "plain_text", "text", "Open Dashboard"),
                                            "url", appBaseUrl
                                    ))
                            )
                    ));
        };
    }

    private String buildPrivilegedActionEmailBody(String actionType,
                                                  String title,
                                                  String summary,
                                                  String actor,
                                                  String entityType,
                                                  String entityKey,
                                                  String status,
                                                  Map<String, Object> metadata) {
        StringBuilder body = new StringBuilder();
        body.append(defaultIfBlank(title, "Privileged access alert"))
                .append("\n\n")
                .append("Action: ")
                .append(defaultIfBlank(actionType, "UNKNOWN"))
                .append('\n')
                .append("Severity: ")
                .append(defaultIfBlank(metadata != null ? Objects.toString(metadata.get("severity"), null) : null, "HIGH"))
                .append('\n')
                .append("Actor: ")
                .append(defaultIfBlank(actor, "system"))
                .append('\n')
                .append("Status: ")
                .append(defaultIfBlank(status, "RECORDED"))
                .append('\n')
                .append("Entity: ")
                .append(defaultIfBlank(entityType, "SECURITY"))
                .append(" / ")
                .append(defaultIfBlank(entityKey, "unknown"))
                .append('\n')
                .append("Summary: ")
                .append(defaultIfBlank(summary, "Security event recorded"))
                .append('\n')
                .append("Dashboard URL: ")
                .append(appBaseUrl)
                .append('\n');
        return body.toString();
    }

    private Object buildPrivilegedActionWebhookPayload(String channelType,
                                                      String actionType,
                                                      String title,
                                                      String summary,
                                                      String actor,
                                                      String entityType,
                                                      String entityKey,
                                                      String status,
                                                      Map<String, Object> metadata) {
        String severity = defaultIfBlank(metadata != null ? Objects.toString(metadata.get("severity"), null) : null, "HIGH");
        return switch (defaultIfBlank(channelType, "SLACK").toUpperCase(Locale.ROOT)) {
            case "MICROSOFT_TEAMS" -> Map.of(
                    "@type", "MessageCard",
                    "@context", "https://schema.org/extensions",
                    "summary", "[RetailINQ] " + defaultIfBlank(title, "Privileged access alert"),
                    "themeColor", "C62828",
                    "title", defaultIfBlank(title, "Privileged access alert"),
                    "sections", List.of(Map.of(
                            "activityTitle", defaultIfBlank(summary, "Privileged access event recorded"),
                            "facts", List.of(
                                    Map.of("name", "Action", "value", defaultIfBlank(actionType, "UNKNOWN")),
                                    Map.of("name", "Severity", "value", severity),
                                    Map.of("name", "Actor", "value", defaultIfBlank(actor, "system")),
                                    Map.of("name", "Status", "value", defaultIfBlank(status, "RECORDED")),
                                    Map.of("name", "Entity", "value", "%s / %s".formatted(
                                            defaultIfBlank(entityType, "SECURITY"),
                                            defaultIfBlank(entityKey, "unknown"))),
                                    Map.of("name", "Dashboard", "value", appBaseUrl)
                            ),
                            "markdown", true
                    )),
                    "potentialAction", List.of(Map.of(
                            "@type", "OpenUri",
                            "name", "Open Dashboard",
                            "targets", List.of(Map.of("os", "default", "uri", appBaseUrl))
                    )));
            default -> Map.of(
                    "text", defaultIfBlank(title, "Privileged access alert"),
                    "blocks", List.of(
                            Map.of(
                                    "type", "header",
                                    "text", Map.of("type", "plain_text", "text", defaultIfBlank(title, "Privileged access alert"))
                            ),
                            Map.of(
                                    "type", "section",
                                    "fields", List.of(
                                            slackField("Action", defaultIfBlank(actionType, "UNKNOWN")),
                                            slackField("Severity", severity),
                                            slackField("Actor", defaultIfBlank(actor, "system")),
                                            slackField("Status", defaultIfBlank(status, "RECORDED")),
                                            slackField("Entity", "%s / %s".formatted(
                                                    defaultIfBlank(entityType, "SECURITY"),
                                                    defaultIfBlank(entityKey, "unknown"))),
                                            slackField("Dashboard", appBaseUrl)
                                    )
                            ),
                            Map.of(
                                    "type", "section",
                                    "text", Map.of("type", "mrkdwn", "text", defaultIfBlank(summary, "Privileged access event recorded"))
                            ),
                            Map.of(
                                    "type", "actions",
                                    "elements", List.of(Map.of(
                                            "type", "button",
                                            "text", Map.of("type", "plain_text", "text", "Open Dashboard"),
                                            "url", appBaseUrl
                                    ))
                            )
                    ));
        };
    }

    private Map<String, Object> slackField(String title, String value) {
        return Map.of("type", "mrkdwn", "text", "*%s*\n%s".formatted(title, defaultIfBlank(value, "-")));
    }

    private List<String> splitCsv(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return List.of();
        }
        return java.util.Arrays.stream(trimmed.split(","))
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .map(item -> item.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ReminderDispatchResult(int eligibleUsers,
                                         int remindedUsers,
                                         int managerEmailsSent,
                                         int broadcastDeliveriesSent) {
    }
}
