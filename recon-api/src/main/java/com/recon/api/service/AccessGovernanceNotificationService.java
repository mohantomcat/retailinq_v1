package com.recon.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.api.domain.AccessGovernanceNotificationJob;
import com.recon.api.domain.TenantAuthConfigEntity;
import com.recon.api.domain.User;
import com.recon.api.repository.AccessGovernanceNotificationJobRepository;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccessGovernanceNotificationService {

    private static final String SECURITY_RECON_VIEW = "SECURITY";
    private static final String PENDING_MANAGER = "PENDING_MANAGER";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RETRY_SCHEDULED = "RETRY_SCHEDULED";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String TYPE_MANAGER_REVIEW_REMINDER = "MANAGER_REVIEW_REMINDER";
    private static final String TYPE_MANAGER_REVIEW_ESCALATION = "MANAGER_REVIEW_ESCALATION";
    private static final String TYPE_PRIVILEGED_ACTION_ALERT = "PRIVILEGED_ACTION_ALERT";
    private static final String CHANNEL_EMAIL = "EMAIL";
    private static final String CHANNEL_MICROSOFT_TEAMS = "MICROSOFT_TEAMS";
    private static final String CHANNEL_SLACK = "SLACK";
    private static final String REMINDER_MODE_MANAGER = "MANAGER_EMAIL";
    private static final String REMINDER_MODE_SUMMARY = "SUMMARY";
    private static final Set<String> ACTIVE_JOB_STATUSES = Set.of(STATUS_PENDING, STATUS_RETRY_SCHEDULED);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final Pattern TEMPLATE_TOKEN_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");
    private static final String DEFAULT_MANAGER_REMINDER_SUBJECT_TEMPLATE =
            "[RetailINQ] Manager access review reminder: {{pendingUserCount}} pending user{{pendingUserPluralSuffix}}";
    private static final String DEFAULT_MANAGER_REMINDER_BODY_TEMPLATE = """
            Quarterly access review follow-up is pending.

            Manager: {{managerName}}
            Pending users: {{pendingUserCount}}
            Users without manager assignment: {{usersWithoutManager}}
            Reminder cadence: {{reminderIntervalDays}} day(s)
            Dashboard URL: {{dashboardUrl}}

            Pending reviews:
            {{pendingUsersList}}
            """;
    private static final String DEFAULT_MANAGER_ESCALATION_SUBJECT_TEMPLATE =
            "[RetailINQ] Access review escalation: {{pendingUserCount}} overdue manager review{{pendingUserPluralSuffix}}";
    private static final String DEFAULT_MANAGER_ESCALATION_BODY_TEMPLATE = """
            Manager access review follow-up is overdue.

            Pending users: {{pendingUserCount}}
            Escalation threshold: {{escalationAfterDays}} day(s) after reminder
            Dashboard URL: {{dashboardUrl}}

            Pending reviews:
            {{pendingUsersList}}
            """;
    private static final String DEFAULT_PRIVILEGED_ALERT_SUBJECT_TEMPLATE =
            "[RetailINQ] Privileged access alert: {{alertTitle}}";
    private static final String DEFAULT_PRIVILEGED_ALERT_BODY_TEMPLATE = """
            {{alertTitle}}

            Action: {{actionType}}
            Severity: {{severity}}
            Actor: {{actor}}
            Status: {{status}}
            Entity: {{entityType}} / {{entityKey}}
            Summary: {{summary}}
            Dashboard URL: {{dashboardUrl}}
            """;

    private final TenantAuthConfigRepository tenantAuthConfigRepository;
    private final UserRepository userRepository;
    private final AccessGovernanceNotificationJobRepository accessGovernanceNotificationJobRepository;
    private final AlertEmailNotificationService alertEmailNotificationService;
    private final AlertWebhookNotificationService alertWebhookNotificationService;
    private final ObjectMapper objectMapper;

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

    @Scheduled(fixedDelayString = "${app.security.access-review.notification-job-fixed-delay-ms:300000}")
    public void runScheduledNotificationJobProcessing() {
        try {
            processDueNotificationJobs(null);
        } catch (Exception ex) {
            log.error("Access governance notification queue processing failed: {}", ex.getMessage(), ex);
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

        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("actionType", defaultIfBlank(actionType, "UNKNOWN"));
        payload.put("alertTitle", defaultIfBlank(title, "Privileged access alert"));
        payload.put("summary", defaultIfBlank(summary, "Privileged access event recorded"));
        payload.put("actor", defaultIfBlank(actor, "system"));
        payload.put("entityType", defaultIfBlank(entityType, "SECURITY"));
        payload.put("entityKey", defaultIfBlank(entityKey, "unknown"));
        payload.put("status", defaultIfBlank(status, "RECORDED"));
        payload.put("severity", defaultIfBlank(metadata != null ? Objects.toString(metadata.get("severity"), null) : null, "HIGH"));
        if (afterState != null) {
            payload.put("afterState", afterState);
        }
        if (metadata != null && !metadata.isEmpty()) {
            payload.put("metadata", metadata);
        }

        for (String recipientEmail : splitCsv(config.getPrivilegedActionAlertEmailRecipients())) {
            enqueueNotificationJob(
                    config,
                    TYPE_PRIVILEGED_ACTION_ALERT,
                    CHANNEL_EMAIL,
                    recipientEmail,
                    "%s:%s:%s:%s:%s".formatted(TYPE_PRIVILEGED_ACTION_ALERT, tenantId, eventId, CHANNEL_EMAIL, recipientEmail),
                    List.of(),
                    payload);
        }
        enqueueNotificationJob(
                config,
                TYPE_PRIVILEGED_ACTION_ALERT,
                CHANNEL_MICROSOFT_TEAMS,
                config.getPrivilegedActionAlertTeamsWebhookUrl(),
                "%s:%s:%s:%s".formatted(TYPE_PRIVILEGED_ACTION_ALERT, tenantId, eventId, CHANNEL_MICROSOFT_TEAMS),
                List.of(),
                payload);
        enqueueNotificationJob(
                config,
                TYPE_PRIVILEGED_ACTION_ALERT,
                CHANNEL_SLACK,
                config.getPrivilegedActionAlertSlackWebhookUrl(),
                "%s:%s:%s:%s".formatted(TYPE_PRIVILEGED_ACTION_ALERT, tenantId, eventId, CHANNEL_SLACK),
                List.of(),
                payload);
        processDueNotificationJobsForTenant(tenantId);
    }

    @Transactional
    void cancelAccessReviewNotifications(String tenantId, UUID userId, String actor, String reason) {
        if (trimToNull(tenantId) == null || userId == null) {
            return;
        }
        List<AccessGovernanceNotificationJob> jobs =
                accessGovernanceNotificationJobRepository.findByTenantIdAndNotificationStatusInOrderByCreatedAtDesc(
                        tenantId,
                        ACTIVE_JOB_STATUSES);
        for (AccessGovernanceNotificationJob job : jobs) {
            if (!Set.of(TYPE_MANAGER_REVIEW_REMINDER, TYPE_MANAGER_REVIEW_ESCALATION).contains(job.getNotificationType())) {
                continue;
            }
            List<UUID> currentUserIds = parseUserIds(job.getReferenceUserIds());
            List<UUID> remainingUserIds = currentUserIds.stream()
                    .filter(existingUserId -> !Objects.equals(existingUserId, userId))
                    .toList();
            if (remainingUserIds.size() == currentUserIds.size()) {
                continue;
            }
            if (remainingUserIds.isEmpty()) {
                job.setNotificationStatus(STATUS_CANCELLED);
                job.setLastError(defaultIfBlank(reason, "Notification cancelled after access review update")
                        + " by "
                        + defaultIfBlank(actor, "system"));
                job.setNextAttemptAt(LocalDateTime.now());
            } else {
                job.setReferenceUserIds(joinUserIds(remainingUserIds));
            }
            accessGovernanceNotificationJobRepository.save(job);
        }
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
                .filter(this::isPendingManagerReview)
                .filter(user -> ignoreCadence || reminderDue(user, now, reminderIntervalDays))
                .toList();

        Map<User, List<User>> pendingByManager = new LinkedHashMap<>();
        for (User pendingUser : pendingUsers) {
            User manager = pendingUser.getManagerUserId() == null ? null : usersById.get(pendingUser.getManagerUserId());
            if (manager == null || !manager.isActive()) {
                continue;
            }
            pendingByManager.computeIfAbsent(manager, ignored -> new ArrayList<>()).add(pendingUser);
        }

        Set<UUID> reminderUserIds = pendingUsers.stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int managerEmailJobs = queueManagerReminderJobs(config, pendingByManager);
        int broadcastJobs = queueReminderBroadcastJobs(config, pendingUsers);
        List<User> escalationCandidates = tenantUsers.stream()
                .filter(User::isActive)
                .filter(user -> !reminderUserIds.contains(user.getId()))
                .filter(user -> escalationDue(config, user, now))
                .toList();
        queueEscalationJobs(config, escalationCandidates);
        processDueNotificationJobsForTenant(config.getTenantId());

        return new ReminderDispatchResult(
                pendingUsers.size(),
                reminderUserIds.size(),
                managerEmailJobs,
                broadcastJobs);
    }

    @Transactional
    void processDueNotificationJobsForTenant(String tenantId) {
        processDueNotificationJobs(tenantId);
    }

    private int queueManagerReminderJobs(TenantAuthConfigEntity config, Map<User, List<User>> pendingByManager) {
        int managerEmailJobs = 0;
        for (Map.Entry<User, List<User>> entry : pendingByManager.entrySet()) {
            User manager = entry.getKey();
            String managerEmail = trimToNull(manager.getEmail());
            if (managerEmail == null) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("mode", REMINDER_MODE_MANAGER);
            payload.put("managerUserId", manager.getId() != null ? manager.getId().toString() : null);
            payload.put("managerName", firstNonBlank(manager.getFullName(), manager.getUsername(), "Manager"));
            if (enqueueNotificationJob(
                    config,
                    TYPE_MANAGER_REVIEW_REMINDER,
                    CHANNEL_EMAIL,
                    managerEmail,
                    "%s:%s:%s:%s".formatted(TYPE_MANAGER_REVIEW_REMINDER, config.getTenantId(), CHANNEL_EMAIL, manager.getId()),
                    entry.getValue().stream().map(User::getId).filter(Objects::nonNull).toList(),
                    payload)) {
                managerEmailJobs++;
            }
        }
        return managerEmailJobs;
    }

    private int queueReminderBroadcastJobs(TenantAuthConfigEntity config, List<User> pendingUsers) {
        List<UUID> summaryUserIds = pendingUsers.stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .toList();
        if (summaryUserIds.isEmpty()) {
            return 0;
        }

        int broadcastJobs = 0;
        Map<String, Object> summaryPayload = Map.of("mode", REMINDER_MODE_SUMMARY);
        for (String recipientEmail : splitCsv(config.getManagerAccessReviewAdditionalEmails())) {
            if (enqueueNotificationJob(
                    config,
                    TYPE_MANAGER_REVIEW_REMINDER,
                    CHANNEL_EMAIL,
                    recipientEmail,
                    "%s:%s:%s:%s".formatted(TYPE_MANAGER_REVIEW_REMINDER, config.getTenantId(), "SUMMARY_EMAIL", recipientEmail),
                    summaryUserIds,
                    summaryPayload)) {
                broadcastJobs++;
            }
        }
        if (enqueueNotificationJob(
                config,
                TYPE_MANAGER_REVIEW_REMINDER,
                CHANNEL_MICROSOFT_TEAMS,
                config.getManagerAccessReviewTeamsWebhookUrl(),
                "%s:%s:%s".formatted(TYPE_MANAGER_REVIEW_REMINDER, config.getTenantId(), CHANNEL_MICROSOFT_TEAMS),
                summaryUserIds,
                summaryPayload)) {
            broadcastJobs++;
        }
        if (enqueueNotificationJob(
                config,
                TYPE_MANAGER_REVIEW_REMINDER,
                CHANNEL_SLACK,
                config.getManagerAccessReviewSlackWebhookUrl(),
                "%s:%s:%s".formatted(TYPE_MANAGER_REVIEW_REMINDER, config.getTenantId(), CHANNEL_SLACK),
                summaryUserIds,
                summaryPayload)) {
            broadcastJobs++;
        }
        return broadcastJobs;
    }

    private int queueEscalationJobs(TenantAuthConfigEntity config, List<User> escalationCandidates) {
        if (config == null || !config.isManagerAccessReviewEscalationEnabled() || escalationCandidates.isEmpty()) {
            return 0;
        }
        List<UUID> escalationUserIds = escalationCandidates.stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .toList();
        if (escalationUserIds.isEmpty()) {
            return 0;
        }

        int queuedJobs = 0;
        Map<String, Object> payload = Map.of("mode", REMINDER_MODE_SUMMARY);
        for (String recipientEmail : splitCsv(config.getManagerAccessReviewEscalationEmailRecipients())) {
            if (enqueueNotificationJob(
                    config,
                    TYPE_MANAGER_REVIEW_ESCALATION,
                    CHANNEL_EMAIL,
                    recipientEmail,
                    "%s:%s:%s:%s".formatted(TYPE_MANAGER_REVIEW_ESCALATION, config.getTenantId(), CHANNEL_EMAIL, recipientEmail),
                    escalationUserIds,
                    payload)) {
                queuedJobs++;
            }
        }
        if (enqueueNotificationJob(
                config,
                TYPE_MANAGER_REVIEW_ESCALATION,
                CHANNEL_MICROSOFT_TEAMS,
                config.getManagerAccessReviewEscalationTeamsWebhookUrl(),
                "%s:%s:%s".formatted(TYPE_MANAGER_REVIEW_ESCALATION, config.getTenantId(), CHANNEL_MICROSOFT_TEAMS),
                escalationUserIds,
                payload)) {
            queuedJobs++;
        }
        if (enqueueNotificationJob(
                config,
                TYPE_MANAGER_REVIEW_ESCALATION,
                CHANNEL_SLACK,
                config.getManagerAccessReviewEscalationSlackWebhookUrl(),
                "%s:%s:%s".formatted(TYPE_MANAGER_REVIEW_ESCALATION, config.getTenantId(), CHANNEL_SLACK),
                escalationUserIds,
                payload)) {
            queuedJobs++;
        }
        return queuedJobs;
    }

    private void processDueNotificationJobs(String tenantId) {
        LocalDateTime now = LocalDateTime.now();
        List<AccessGovernanceNotificationJob> dueJobs = trimToNull(tenantId) == null
                ? accessGovernanceNotificationJobRepository
                .findTop100ByNotificationStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscCreatedAtAsc(
                        ACTIVE_JOB_STATUSES,
                        now)
                : accessGovernanceNotificationJobRepository
                .findTop100ByTenantIdAndNotificationStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscCreatedAtAsc(
                        tenantId,
                        ACTIVE_JOB_STATUSES,
                        now);
        for (AccessGovernanceNotificationJob job : dueJobs) {
            try {
                processJob(job);
            } catch (Exception ex) {
                log.error("Access governance notification job {} failed unexpectedly: {}", job.getId(), ex.getMessage(), ex);
                markJobForRetry(job, "Unexpected processing failure: " + ex.getMessage(), now);
            }
        }
    }

    private void processJob(AccessGovernanceNotificationJob job) {
        TenantAuthConfigEntity config = tenantAuthConfigRepository.findById(job.getTenantId()).orElse(null);
        if (config == null) {
            cancelJob(job, "Tenant notification policy was removed");
            return;
        }

        NotificationMessage message = buildMessage(job, config);
        if (message == null) {
            cancelJob(job, "Notification no longer applies to the current reminder state");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        job.setAttemptCount(job.getAttemptCount() + 1);
        job.setLastAttemptAt(now);

        boolean delivered;
        try {
            delivered = deliverMessage(job, message);
        } catch (Exception ex) {
            delivered = false;
            log.warn("Access governance notification delivery threw for job {}: {}", job.getId(), ex.getMessage());
        }

        if (delivered) {
            job.setNotificationStatus(STATUS_SENT);
            job.setDeliveredAt(now);
            job.setLastError(null);
            job.setNextAttemptAt(now);
            accessGovernanceNotificationJobRepository.save(job);
            updateReminderStateAfterDelivery(job, message.referenceUsers(), now);
            return;
        }
        markJobForRetry(job, "Delivery returned unsuccessful status", now);
    }

    private NotificationMessage buildMessage(AccessGovernanceNotificationJob job, TenantAuthConfigEntity config) {
        return switch (defaultIfBlank(job.getNotificationType(), "").toUpperCase(Locale.ROOT)) {
            case TYPE_MANAGER_REVIEW_REMINDER -> buildManagerReminderMessage(job, config);
            case TYPE_MANAGER_REVIEW_ESCALATION -> buildManagerEscalationMessage(job, config);
            case TYPE_PRIVILEGED_ACTION_ALERT -> buildPrivilegedActionMessage(job, config);
            default -> null;
        };
    }

    private NotificationMessage buildManagerReminderMessage(AccessGovernanceNotificationJob job,
                                                            TenantAuthConfigEntity config) {
        if (!config.isManagerAccessReviewRemindersEnabled()) {
            return null;
        }
        Map<String, Object> payload = readPayload(job.getPayloadData());
        String mode = defaultIfBlank(Objects.toString(payload.get("mode"), null), REMINDER_MODE_SUMMARY);
        Map<UUID, User> usersById = loadUsersById(job.getTenantId());
        List<User> pendingUsers = parseUserIds(job.getReferenceUserIds()).stream()
                .map(usersById::get)
                .filter(Objects::nonNull)
                .filter(User::isActive)
                .filter(this::isPendingManagerReview)
                .toList();

        User manager = null;
        if (REMINDER_MODE_MANAGER.equalsIgnoreCase(mode)) {
            manager = usersById.get(parseUuid(payload.get("managerUserId")));
            if (manager == null || !manager.isActive()) {
                return null;
            }
            UUID managerId = manager.getId();
            pendingUsers = pendingUsers.stream()
                    .filter(user -> Objects.equals(user.getManagerUserId(), managerId))
                    .toList();
        }
        if (pendingUsers.isEmpty()) {
            return null;
        }

        long usersWithoutManager = pendingUsers.stream()
                .filter(user -> user.getManagerUserId() == null || usersById.get(user.getManagerUserId()) == null)
                .count();
        Map<String, String> tokens = buildReminderTokens(
                config.getTenantId(),
                firstNonBlank(
                        Objects.toString(payload.get("managerName"), null),
                        manager != null ? manager.getFullName() : null,
                        manager != null ? manager.getUsername() : null,
                        "Access manager"),
                pendingUsers,
                usersWithoutManager,
                config.getManagerAccessReviewReminderIntervalDays(),
                0);
        String subject = renderTemplate(
                defaultIfBlank(config.getManagerAccessReviewReminderSubjectTemplate(), DEFAULT_MANAGER_REMINDER_SUBJECT_TEMPLATE),
                tokens);
        String body = renderTemplate(
                defaultIfBlank(config.getManagerAccessReviewReminderBodyTemplate(), DEFAULT_MANAGER_REMINDER_BODY_TEMPLATE),
                tokens);
        return new NotificationMessage(job.getTargetKey(), job.getChannelType(), subject, body, pendingUsers, "EF6C00");
    }

    private NotificationMessage buildManagerEscalationMessage(AccessGovernanceNotificationJob job,
                                                              TenantAuthConfigEntity config) {
        if (!config.isManagerAccessReviewEscalationEnabled()) {
            return null;
        }
        Map<UUID, User> usersById = loadUsersById(job.getTenantId());
        List<User> escalationUsers = parseUserIds(job.getReferenceUserIds()).stream()
                .map(usersById::get)
                .filter(Objects::nonNull)
                .filter(User::isActive)
                .filter(user -> escalationDue(config, user, LocalDateTime.now()))
                .toList();
        if (escalationUsers.isEmpty()) {
            return null;
        }

        long usersWithoutManager = escalationUsers.stream()
                .filter(user -> user.getManagerUserId() == null || usersById.get(user.getManagerUserId()) == null)
                .count();
        Map<String, String> tokens = buildReminderTokens(
                config.getTenantId(),
                "Access administrators",
                escalationUsers,
                usersWithoutManager,
                config.getManagerAccessReviewReminderIntervalDays(),
                config.getManagerAccessReviewEscalationAfterDays());
        String subject = renderTemplate(
                defaultIfBlank(config.getManagerAccessReviewEscalationSubjectTemplate(), DEFAULT_MANAGER_ESCALATION_SUBJECT_TEMPLATE),
                tokens);
        String body = renderTemplate(
                defaultIfBlank(config.getManagerAccessReviewEscalationBodyTemplate(), DEFAULT_MANAGER_ESCALATION_BODY_TEMPLATE),
                tokens);
        return new NotificationMessage(job.getTargetKey(), job.getChannelType(), subject, body, escalationUsers, "C62828");
    }

    private NotificationMessage buildPrivilegedActionMessage(AccessGovernanceNotificationJob job,
                                                             TenantAuthConfigEntity config) {
        if (!config.isPrivilegedActionAlertsEnabled()) {
            return null;
        }
        Map<String, Object> payload = readPayload(job.getPayloadData());
        Map<String, String> tokens = new LinkedHashMap<>();
        tokens.put("tenantId", config.getTenantId());
        tokens.put("dashboardUrl", appBaseUrl);
        tokens.put("actionType", defaultIfBlank(Objects.toString(payload.get("actionType"), null), "UNKNOWN"));
        tokens.put("alertTitle", defaultIfBlank(Objects.toString(payload.get("alertTitle"), null), "Privileged access alert"));
        tokens.put("summary", defaultIfBlank(Objects.toString(payload.get("summary"), null), "Privileged access event recorded"));
        tokens.put("actor", defaultIfBlank(Objects.toString(payload.get("actor"), null), "system"));
        tokens.put("entityType", defaultIfBlank(Objects.toString(payload.get("entityType"), null), "SECURITY"));
        tokens.put("entityKey", defaultIfBlank(Objects.toString(payload.get("entityKey"), null), "unknown"));
        tokens.put("status", defaultIfBlank(Objects.toString(payload.get("status"), null), "RECORDED"));
        tokens.put("severity", defaultIfBlank(Objects.toString(payload.get("severity"), null), "HIGH"));
        String subject = renderTemplate(
                defaultIfBlank(config.getPrivilegedActionAlertSubjectTemplate(), DEFAULT_PRIVILEGED_ALERT_SUBJECT_TEMPLATE),
                tokens);
        String body = renderTemplate(
                defaultIfBlank(config.getPrivilegedActionAlertBodyTemplate(), DEFAULT_PRIVILEGED_ALERT_BODY_TEMPLATE),
                tokens);
        return new NotificationMessage(job.getTargetKey(), job.getChannelType(), subject, body, List.of(), resolveSeverityColor(tokens.get("severity")));
    }

    private boolean deliverMessage(AccessGovernanceNotificationJob job, NotificationMessage message) {
        UUID eventId = UUID.randomUUID();
        String channelType = defaultIfBlank(job.getChannelType(), CHANNEL_EMAIL).toUpperCase(Locale.ROOT);
        if (CHANNEL_EMAIL.equals(channelType)) {
            return alertEmailNotificationService.sendDirectEmail(
                    job.getTenantId(),
                    SECURITY_RECON_VIEW,
                    eventId,
                    message.target(),
                    message.subject(),
                    message.body());
        }
        return alertWebhookNotificationService.sendDirectWebhook(
                job.getTenantId(),
                SECURITY_RECON_VIEW,
                eventId,
                channelType,
                message.target(),
                buildWebhookPayload(channelType, message.subject(), message.body(), message.themeColor()));
    }

    private void updateReminderStateAfterDelivery(AccessGovernanceNotificationJob job,
                                                  List<User> affectedUsers,
                                                  LocalDateTime deliveredAt) {
        if (affectedUsers == null || affectedUsers.isEmpty()) {
            return;
        }
        if (TYPE_MANAGER_REVIEW_REMINDER.equalsIgnoreCase(job.getNotificationType())) {
            for (User user : affectedUsers) {
                user.setAccessReviewLastReminderAt(deliveredAt);
                user.setAccessReviewReminderAcknowledgedAt(null);
                user.setAccessReviewReminderAcknowledgedBy(null);
                user.setAccessReviewReminderAckNote(null);
                user.setAccessReviewLastEscalatedAt(null);
            }
            userRepository.saveAll(affectedUsers);
            return;
        }
        if (TYPE_MANAGER_REVIEW_ESCALATION.equalsIgnoreCase(job.getNotificationType())) {
            for (User user : affectedUsers) {
                user.setAccessReviewLastEscalatedAt(deliveredAt);
            }
            userRepository.saveAll(affectedUsers);
        }
    }

    private void markJobForRetry(AccessGovernanceNotificationJob job,
                                 String errorMessage,
                                 LocalDateTime now) {
        if (job.getAttemptCount() >= Math.max(1, job.getMaxAttempts())) {
            job.setNotificationStatus(STATUS_FAILED);
            job.setLastError(defaultIfBlank(errorMessage, "Delivery failed"));
            job.setNextAttemptAt(now);
            accessGovernanceNotificationJobRepository.save(job);
            return;
        }
        job.setNotificationStatus(STATUS_RETRY_SCHEDULED);
        job.setLastError(defaultIfBlank(errorMessage, "Delivery failed"));
        job.setNextAttemptAt(now.plusMinutes(resolveBackoffMinutes(job)));
        accessGovernanceNotificationJobRepository.save(job);
    }

    private void cancelJob(AccessGovernanceNotificationJob job, String reason) {
        job.setNotificationStatus(STATUS_CANCELLED);
        job.setLastError(reason);
        job.setNextAttemptAt(LocalDateTime.now());
        accessGovernanceNotificationJobRepository.save(job);
    }

    private boolean enqueueNotificationJob(TenantAuthConfigEntity config,
                                           String notificationType,
                                           String channelType,
                                           String target,
                                           String contextKey,
                                           Collection<UUID> referenceUserIds,
                                           Map<String, Object> payload) {
        String trimmedTarget = trimToNull(target);
        if (config == null || trimToNull(channelType) == null || trimmedTarget == null) {
            return false;
        }
        String trimmedContextKey = trimToNull(contextKey);
        if (trimmedContextKey != null
                && accessGovernanceNotificationJobRepository.existsByNotificationContextKeyAndNotificationStatusIn(
                trimmedContextKey,
                ACTIVE_JOB_STATUSES)) {
            return false;
        }
        accessGovernanceNotificationJobRepository.save(AccessGovernanceNotificationJob.builder()
                .tenantId(config.getTenantId())
                .notificationType(notificationType)
                .channelType(channelType.toUpperCase(Locale.ROOT))
                .targetKey(trimmedTarget)
                .notificationContextKey(trimmedContextKey)
                .referenceUserIds(joinUserIds(referenceUserIds))
                .payloadData(writePayload(payload))
                .notificationStatus(STATUS_PENDING)
                .attemptCount(0)
                .maxAttempts(Math.max(1, config.getGovernanceNotificationMaxAttempts()))
                .backoffMinutes(Math.max(1, config.getGovernanceNotificationBackoffMinutes()))
                .nextAttemptAt(LocalDateTime.now())
                .build());
        return true;
    }

    private Object buildWebhookPayload(String channelType,
                                       String title,
                                       String body,
                                       String themeColor) {
        String safeTitle = defaultIfBlank(title, "Access governance notification");
        String safeBody = defaultIfBlank(body, "Open the dashboard for details.");
        return switch (defaultIfBlank(channelType, CHANNEL_SLACK).toUpperCase(Locale.ROOT)) {
            case CHANNEL_MICROSOFT_TEAMS -> Map.of(
                    "@type", "MessageCard",
                    "@context", "https://schema.org/extensions",
                    "summary", safeTitle,
                    "themeColor", defaultIfBlank(themeColor, "005A9C"),
                    "title", safeTitle,
                    "text", safeBody.replace("\n", "<br/>"),
                    "potentialAction", List.of(Map.of(
                            "@type", "OpenUri",
                            "name", "Open Dashboard",
                            "targets", List.of(Map.of("os", "default", "uri", appBaseUrl))
                    )));
            default -> Map.of(
                    "text", safeTitle,
                    "blocks", List.of(
                            Map.of(
                                    "type", "header",
                                    "text", Map.of("type", "plain_text", "text", safeTitle)
                            ),
                            Map.of(
                                    "type", "section",
                                    "text", Map.of("type", "mrkdwn", "text", safeBody)
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

    private Map<String, String> buildReminderTokens(String tenantId,
                                                    String managerName,
                                                    List<User> users,
                                                    long usersWithoutManager,
                                                    int reminderIntervalDays,
                                                    int escalationAfterDays) {
        Map<String, String> tokens = new LinkedHashMap<>();
        int pendingUserCount = users != null ? users.size() : 0;
        tokens.put("tenantId", defaultIfBlank(tenantId, "-"));
        tokens.put("dashboardUrl", appBaseUrl);
        tokens.put("managerName", defaultIfBlank(managerName, "Access manager"));
        tokens.put("pendingUserCount", String.valueOf(pendingUserCount));
        tokens.put("pendingUserPluralSuffix", pendingUserCount == 1 ? "" : "s");
        tokens.put("usersWithoutManager", String.valueOf(usersWithoutManager));
        tokens.put("reminderIntervalDays", String.valueOf(Math.max(1, reminderIntervalDays)));
        tokens.put("escalationAfterDays", String.valueOf(Math.max(0, escalationAfterDays)));
        tokens.put("pendingUsersList", buildPendingUsersList(users));
        return tokens;
    }

    private String buildPendingUsersList(List<User> users) {
        if (users == null || users.isEmpty()) {
            return "- No pending users";
        }
        StringBuilder builder = new StringBuilder();
        for (User user : users) {
            builder.append("- ")
                    .append(firstNonBlank(user.getFullName(), user.getUsername(), "User"))
                    .append(" (")
                    .append(defaultIfBlank(user.getUsername(), "unknown"))
                    .append(")");
            if (user.getAccessReviewDueAt() != null) {
                builder.append(" | Due ").append(DATE_FORMATTER.format(user.getAccessReviewDueAt()));
            }
            if (user.getRoles() != null && !user.getRoles().isEmpty()) {
                builder.append(" | Roles: ")
                        .append(user.getRoles().stream()
                                .map(role -> role.getName())
                                .filter(Objects::nonNull)
                                .sorted()
                                .collect(Collectors.joining(", ")));
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private String renderTemplate(String template, Map<String, String> tokens) {
        String resolvedTemplate = defaultIfBlank(template, "");
        Matcher matcher = TEMPLATE_TOKEN_PATTERN.matcher(resolvedTemplate);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = tokens != null ? defaultIfBlank(tokens.get(key), "") : "";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString().trim();
    }

    private boolean reminderDue(User user, LocalDateTime now, int reminderIntervalDays) {
        LocalDateTime lastReminderAt = user.getAccessReviewLastReminderAt();
        return lastReminderAt == null || !lastReminderAt.isAfter(now.minusDays(reminderIntervalDays));
    }

    private boolean escalationDue(TenantAuthConfigEntity config, User user, LocalDateTime now) {
        if (config == null || !config.isManagerAccessReviewEscalationEnabled() || user == null || !isPendingManagerReview(user)) {
            return false;
        }
        LocalDateTime lastReminderAt = user.getAccessReviewLastReminderAt();
        if (lastReminderAt == null || lastReminderAt.isAfter(now.minusDays(Math.max(1, config.getManagerAccessReviewEscalationAfterDays())))) {
            return false;
        }
        LocalDateTime acknowledgedAt = user.getAccessReviewReminderAcknowledgedAt();
        if (acknowledgedAt != null && !acknowledgedAt.isBefore(lastReminderAt)) {
            return false;
        }
        LocalDateTime lastEscalatedAt = user.getAccessReviewLastEscalatedAt();
        return lastEscalatedAt == null || lastEscalatedAt.isBefore(lastReminderAt);
    }

    private boolean isPendingManagerReview(User user) {
        return user != null
                && PENDING_MANAGER.equalsIgnoreCase(defaultIfBlank(user.getAccessReviewStatus(), ""));
    }

    private long resolveBackoffMinutes(AccessGovernanceNotificationJob job) {
        long baseMinutes = Math.max(1, job.getBackoffMinutes());
        int exponent = Math.max(0, job.getAttemptCount() - 1);
        long multiplier = 1L << Math.min(exponent, 10);
        return Math.min(baseMinutes * multiplier, 7L * 24L * 60L);
    }

    private Map<UUID, User> loadUsersById(String tenantId) {
        return userRepository.findByTenantId(tenantId).stream()
                .filter(user -> user.getId() != null)
                .collect(Collectors.toMap(
                        User::getId,
                        user -> user,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private Map<String, Object> readPayload(String payloadData) {
        String trimmed = trimToNull(payloadData);
        if (trimmed == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(trimmed, new TypeReference<>() {});
        } catch (Exception ex) {
            log.warn("Failed to parse access governance notification payload: {}", ex.getMessage());
            return Map.of();
        }
    }

    private String writePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize governance notification payload", ex);
        }
    }

    private List<UUID> parseUserIds(String referenceUserIds) {
        String trimmed = trimToNull(referenceUserIds);
        if (trimmed == null) {
            return List.of();
        }
        List<UUID> userIds = new ArrayList<>();
        for (String rawId : trimmed.split(",")) {
            String normalized = trimToNull(rawId);
            if (normalized == null) {
                continue;
            }
            try {
                userIds.add(UUID.fromString(normalized));
            } catch (IllegalArgumentException ex) {
                log.warn("Ignoring invalid access governance notification user id {}", normalized);
            }
        }
        return userIds;
    }

    private UUID parseUuid(Object value) {
        String trimmed = trimToNull(Objects.toString(value, null));
        if (trimmed == null) {
            return null;
        }
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String joinUserIds(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return null;
        }
        return userIds.stream()
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .distinct()
                .collect(Collectors.joining(","));
    }

    private String resolveSeverityColor(String severity) {
        return switch (defaultIfBlank(severity, "HIGH").toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> "B71C1C";
            case "HIGH" -> "C62828";
            default -> "005A9C";
        };
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

    private record NotificationMessage(String target,
                                       String channelType,
                                       String subject,
                                       String body,
                                       List<User> referenceUsers,
                                       String themeColor) {
    }
}
