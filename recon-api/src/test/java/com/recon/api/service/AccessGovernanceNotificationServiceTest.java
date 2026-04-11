package com.recon.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.api.domain.AccessGovernanceNotificationJob;
import com.recon.api.domain.TenantAuthConfigEntity;
import com.recon.api.domain.User;
import com.recon.api.repository.AccessGovernanceNotificationJobRepository;
import com.recon.api.repository.TenantAuthConfigRepository;
import com.recon.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccessGovernanceNotificationServiceTest {

    @Mock
    private TenantAuthConfigRepository tenantAuthConfigRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccessGovernanceNotificationJobRepository accessGovernanceNotificationJobRepository;

    @Mock
    private AlertEmailNotificationService alertEmailNotificationService;

    @Mock
    private AlertWebhookNotificationService alertWebhookNotificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<AccessGovernanceNotificationJob> storedJobs = new ArrayList<>();

    private AccessGovernanceNotificationService accessGovernanceNotificationService;

    @BeforeEach
    void setUp() {
        accessGovernanceNotificationService = new AccessGovernanceNotificationService(
                tenantAuthConfigRepository,
                userRepository,
                accessGovernanceNotificationJobRepository,
                alertEmailNotificationService,
                alertWebhookNotificationService,
                objectMapper);
        ReflectionTestUtils.setField(accessGovernanceNotificationService, "appBaseUrl", "https://retailinq.example/app");

        when(accessGovernanceNotificationJobRepository.save(any(AccessGovernanceNotificationJob.class)))
                .thenAnswer(invocation -> {
                    AccessGovernanceNotificationJob job = invocation.getArgument(0, AccessGovernanceNotificationJob.class);
                    if (job.getId() == null) {
                        job.setId(UUID.randomUUID());
                    }
                    storedJobs.removeIf(existing -> existing.getId().equals(job.getId()));
                    storedJobs.add(job);
                    return job;
                });
        when(accessGovernanceNotificationJobRepository.existsByNotificationContextKeyAndNotificationStatusIn(anyString(), any()))
                .thenAnswer(invocation -> {
                    String contextKey = invocation.getArgument(0, String.class);
                    Collection<String> statuses = invocation.getArgument(1);
                    return storedJobs.stream().anyMatch(job ->
                            contextKey.equals(job.getNotificationContextKey())
                                    && statuses.contains(job.getNotificationStatus()));
                });
        when(accessGovernanceNotificationJobRepository.findTop100ByTenantIdAndNotificationStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscCreatedAtAsc(anyString(), any(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    String tenantId = invocation.getArgument(0, String.class);
                    Collection<String> statuses = invocation.getArgument(1);
                    LocalDateTime dueAt = invocation.getArgument(2, LocalDateTime.class);
                    return storedJobs.stream()
                            .filter(job -> tenantId.equals(job.getTenantId()))
                            .filter(job -> statuses.contains(job.getNotificationStatus()))
                            .filter(job -> job.getNextAttemptAt() == null || !job.getNextAttemptAt().isAfter(dueAt))
                            .sorted(Comparator.comparing(AccessGovernanceNotificationJob::getNextAttemptAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                                    .thenComparing(AccessGovernanceNotificationJob::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                            .limit(100)
                            .toList();
                });
        when(accessGovernanceNotificationJobRepository.findTop100ByNotificationStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscCreatedAtAsc(any(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    Collection<String> statuses = invocation.getArgument(0);
                    LocalDateTime dueAt = invocation.getArgument(1, LocalDateTime.class);
                    return storedJobs.stream()
                            .filter(job -> statuses.contains(job.getNotificationStatus()))
                            .filter(job -> job.getNextAttemptAt() == null || !job.getNextAttemptAt().isAfter(dueAt))
                            .sorted(Comparator.comparing(AccessGovernanceNotificationJob::getNextAttemptAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                                    .thenComparing(AccessGovernanceNotificationJob::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                            .limit(100)
                            .toList();
                });
        when(accessGovernanceNotificationJobRepository.findByTenantIdAndNotificationStatusInOrderByCreatedAtDesc(anyString(), any()))
                .thenAnswer(invocation -> {
                    String tenantId = invocation.getArgument(0, String.class);
                    Collection<String> statuses = invocation.getArgument(1);
                    return storedJobs.stream()
                            .filter(job -> tenantId.equals(job.getTenantId()))
                            .filter(job -> statuses.contains(job.getNotificationStatus()))
                            .sorted(Comparator.comparing(AccessGovernanceNotificationJob::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                            .toList();
                });
        when(accessGovernanceNotificationJobRepository.findTop50ByTenantIdOrderByCreatedAtDesc(anyString()))
                .thenAnswer(invocation -> {
                    String tenantId = invocation.getArgument(0, String.class);
                    return storedJobs.stream()
                            .filter(job -> tenantId.equals(job.getTenantId()))
                            .sorted(Comparator.comparing(AccessGovernanceNotificationJob::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                            .limit(50)
                            .toList();
                });
        when(userRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void dispatchManagerAccessReviewRemindersUsesManagerEmailAndBroadcastChannels() {
        UUID managerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        TenantAuthConfigEntity config = TenantAuthConfigEntity.builder()
                .tenantId("tenant-india")
                .managerAccessReviewRemindersEnabled(true)
                .managerAccessReviewReminderIntervalDays(7)
                .governanceNotificationMaxAttempts(3)
                .governanceNotificationBackoffMinutes(15)
                .managerAccessReviewAdditionalEmails("security@example.com")
                .managerAccessReviewTeamsWebhookUrl("https://teams.example/webhook")
                .managerAccessReviewSlackWebhookUrl("https://slack.example/webhook")
                .build();
        User manager = User.builder()
                .id(managerId)
                .tenantId("tenant-india")
                .username("manager")
                .fullName("Duty Manager")
                .email("manager@example.com")
                .active(true)
                .build();
        User pendingUser = User.builder()
                .id(userId)
                .tenantId("tenant-india")
                .username("alpha")
                .fullName("Alpha User")
                .email("alpha@example.com")
                .active(true)
                .managerUserId(managerId)
                .accessReviewStatus("PENDING_MANAGER")
                .accessReviewDueAt(LocalDateTime.now().minusDays(1))
                .accessReviewLastReminderAt(LocalDateTime.now().minusDays(8))
                .build();

        when(tenantAuthConfigRepository.findById("tenant-india")).thenReturn(Optional.of(config));
        when(userRepository.findByTenantId("tenant-india")).thenReturn(List.of(manager, pendingUser));
        when(alertEmailNotificationService.sendDirectEmail(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        when(alertWebhookNotificationService.sendDirectWebhook(anyString(), anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(true);

        AccessGovernanceNotificationService.ReminderDispatchResult result =
                accessGovernanceNotificationService.dispatchManagerAccessReviewRemindersForTenant("tenant-india", false);

        assertEquals(1, result.eligibleUsers());
        assertEquals(1, result.remindedUsers());
        assertEquals(1, result.managerEmailsSent());
        assertEquals(3, result.broadcastDeliveriesSent());
        assertNotNull(pendingUser.getAccessReviewLastReminderAt());
        verify(alertEmailNotificationService, org.mockito.Mockito.times(2))
                .sendDirectEmail(anyString(), anyString(), any(), anyString(), anyString(), anyString());
        verify(alertWebhookNotificationService, org.mockito.Mockito.times(2))
                .sendDirectWebhook(anyString(), anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    void processDueNotificationJobsSchedulesRetryAfterFailure() throws Exception {
        TenantAuthConfigEntity config = TenantAuthConfigEntity.builder()
                .tenantId("tenant-india")
                .privilegedActionAlertsEnabled(true)
                .governanceNotificationMaxAttempts(3)
                .governanceNotificationBackoffMinutes(15)
                .build();
        AccessGovernanceNotificationJob job = AccessGovernanceNotificationJob.builder()
                .id(UUID.randomUUID())
                .tenantId("tenant-india")
                .notificationType("PRIVILEGED_ACTION_ALERT")
                .channelType("EMAIL")
                .targetKey("security@example.com")
                .notificationContextKey("privileged-alert:tenant-india:email")
                .payloadData(objectMapper.writeValueAsString(Map.of(
                        "alertTitle", "Emergency admin access granted",
                        "actionType", "EMERGENCY_ACCESS_GRANTED",
                        "summary", "alpha received temporary privileged access",
                        "actor", "security.admin",
                        "entityType", "EMERGENCY_ACCESS",
                        "entityKey", "grant-1",
                        "status", "ACTIVE",
                        "severity", "CRITICAL"
                )))
                .notificationStatus("PENDING")
                .attemptCount(0)
                .maxAttempts(3)
                .backoffMinutes(15)
                .nextAttemptAt(LocalDateTime.now().minusMinutes(1))
                .createdAt(LocalDateTime.now().minusMinutes(2))
                .build();
        storedJobs.add(job);

        when(tenantAuthConfigRepository.findById("tenant-india")).thenReturn(Optional.of(config));
        when(alertEmailNotificationService.sendDirectEmail(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(false);

        accessGovernanceNotificationService.processDueNotificationJobsForTenant("tenant-india");

        assertEquals("RETRY_SCHEDULED", job.getNotificationStatus());
        assertEquals(1, job.getAttemptCount());
        assertTrue(job.getNextAttemptAt().isAfter(LocalDateTime.now().plusMinutes(10)));
        assertNotNull(job.getLastError());
    }

    @Test
    void resendManagerAccessReviewReminderQueuesSingleManagerDeliveryAndHistory() {
        UUID managerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        TenantAuthConfigEntity config = TenantAuthConfigEntity.builder()
                .tenantId("tenant-india")
                .managerAccessReviewRemindersEnabled(true)
                .managerAccessReviewReminderIntervalDays(7)
                .governanceNotificationMaxAttempts(3)
                .governanceNotificationBackoffMinutes(15)
                .build();
        User manager = User.builder()
                .id(managerId)
                .tenantId("tenant-india")
                .username("store.manager")
                .fullName("Store Manager")
                .email("manager@example.com")
                .active(true)
                .build();
        User pendingUser = User.builder()
                .id(userId)
                .tenantId("tenant-india")
                .username("associate")
                .fullName("Associate User")
                .email("associate@example.com")
                .active(true)
                .managerUserId(managerId)
                .accessReviewStatus("PENDING_MANAGER")
                .accessReviewDueAt(LocalDateTime.now().minusDays(1))
                .build();

        when(tenantAuthConfigRepository.findById("tenant-india")).thenReturn(Optional.of(config));
        when(userRepository.findByIdAndTenantId(userId, "tenant-india")).thenReturn(Optional.of(pendingUser));
        when(userRepository.findByTenantId("tenant-india")).thenReturn(List.of(manager, pendingUser));
        when(alertEmailNotificationService.sendDirectEmail(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        var result = accessGovernanceNotificationService.resendManagerAccessReviewReminder(
                "tenant-india",
                userId,
                "security.admin");

        assertEquals("MANAGER", result.getNotificationTier());
        assertEquals(1, result.getQueuedJobs());
        assertNotNull(pendingUser.getAccessReviewLastReminderAt());
        verify(alertEmailNotificationService).sendDirectEmail(
                eq("tenant-india"),
                eq("SECURITY"),
                any(),
                eq("manager@example.com"),
                anyString(),
                anyString());

        var history = accessGovernanceNotificationService.listRecentNotificationHistory("tenant-india");
        assertEquals(1, history.size());
        assertTrue(history.get(0).getTargetLabel().contains("Store Manager"));
        assertEquals(List.of("Associate User"), history.get(0).getReferenceUsers());
    }

    @Test
    void dispatchManagerAccessReviewRemindersEscalatesToNextManagerAfterAcknowledgement() {
        UUID regionalManagerId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID managerId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID userId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        TenantAuthConfigEntity config = TenantAuthConfigEntity.builder()
                .tenantId("tenant-india")
                .managerAccessReviewRemindersEnabled(true)
                .managerAccessReviewReminderIntervalDays(30)
                .managerAccessReviewNextTierEscalationEnabled(true)
                .managerAccessReviewNextTierEscalationAfterDays(2)
                .governanceNotificationMaxAttempts(3)
                .governanceNotificationBackoffMinutes(15)
                .build();
        User regionalManager = User.builder()
                .id(regionalManagerId)
                .tenantId("tenant-india")
                .username("regional.manager")
                .fullName("Regional Manager")
                .email("regional@example.com")
                .active(true)
                .build();
        User manager = User.builder()
                .id(managerId)
                .tenantId("tenant-india")
                .username("store.manager")
                .fullName("Store Manager")
                .email("manager@example.com")
                .managerUserId(regionalManagerId)
                .active(true)
                .build();
        User pendingUser = User.builder()
                .id(userId)
                .tenantId("tenant-india")
                .username("associate")
                .fullName("Associate User")
                .email("associate@example.com")
                .active(true)
                .managerUserId(managerId)
                .accessReviewStatus("PENDING_MANAGER")
                .accessReviewDueAt(LocalDateTime.now().minusDays(1))
                .accessReviewLastReminderAt(LocalDateTime.now().minusDays(5))
                .accessReviewReminderAcknowledgedAt(LocalDateTime.now().minusDays(4))
                .accessReviewReminderAcknowledgedBy("store.manager")
                .build();

        when(tenantAuthConfigRepository.findById("tenant-india")).thenReturn(Optional.of(config));
        when(userRepository.findByTenantId("tenant-india")).thenReturn(List.of(regionalManager, manager, pendingUser));
        when(alertEmailNotificationService.sendDirectEmail(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        accessGovernanceNotificationService.dispatchManagerAccessReviewRemindersForTenant("tenant-india", false);

        assertNotNull(pendingUser.getAccessReviewLastNextTierEscalatedAt());
        verify(alertEmailNotificationService).sendDirectEmail(
                eq("tenant-india"),
                eq("SECURITY"),
                any(),
                eq("regional@example.com"),
                anyString(),
                anyString());
    }
}
