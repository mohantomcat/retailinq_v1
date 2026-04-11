package com.recon.api.service;

import com.recon.api.domain.TenantAuthConfigEntity;
import com.recon.api.domain.User;
import com.recon.api.repository.TenantAuthConfigRepository;
import com.recon.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessGovernanceNotificationServiceTest {

    @Mock
    private TenantAuthConfigRepository tenantAuthConfigRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AlertEmailNotificationService alertEmailNotificationService;

    @Mock
    private AlertWebhookNotificationService alertWebhookNotificationService;

    private AccessGovernanceNotificationService accessGovernanceNotificationService;

    @BeforeEach
    void setUp() {
        accessGovernanceNotificationService = new AccessGovernanceNotificationService(
                tenantAuthConfigRepository,
                userRepository,
                alertEmailNotificationService,
                alertWebhookNotificationService);
        ReflectionTestUtils.setField(accessGovernanceNotificationService, "appBaseUrl", "https://retailinq.example/app");
    }

    @Test
    void dispatchManagerAccessReviewRemindersUsesManagerEmailAndBroadcastChannels() {
        UUID managerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        TenantAuthConfigEntity config = TenantAuthConfigEntity.builder()
                .tenantId("tenant-india")
                .managerAccessReviewRemindersEnabled(true)
                .managerAccessReviewReminderIntervalDays(7)
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
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0, User.class));

        AccessGovernanceNotificationService.ReminderDispatchResult result =
                accessGovernanceNotificationService.dispatchManagerAccessReviewRemindersForTenant("tenant-india", false);

        assertEquals(1, result.eligibleUsers());
        assertEquals(1, result.remindedUsers());
        assertEquals(1, result.managerEmailsSent());
        assertEquals(3, result.broadcastDeliveriesSent());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertNotNull(userCaptor.getValue().getAccessReviewLastReminderAt());
        verify(alertEmailNotificationService, times(2))
                .sendDirectEmail(anyString(), anyString(), any(), anyString(), anyString(), anyString());
        verify(alertWebhookNotificationService, times(2))
                .sendDirectWebhook(anyString(), anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    void notifyPrivilegedActionDeliversConfiguredChannels() {
        TenantAuthConfigEntity config = TenantAuthConfigEntity.builder()
                .tenantId("tenant-india")
                .privilegedActionAlertsEnabled(true)
                .privilegedActionAlertEmailRecipients("security@example.com,ops@example.com")
                .privilegedActionAlertTeamsWebhookUrl("https://teams.example/webhook")
                .privilegedActionAlertSlackWebhookUrl("https://slack.example/webhook")
                .build();

        when(tenantAuthConfigRepository.findById("tenant-india")).thenReturn(Optional.of(config));
        when(alertEmailNotificationService.sendDirectEmail(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        when(alertWebhookNotificationService.sendDirectWebhook(anyString(), anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(true);

        accessGovernanceNotificationService.notifyPrivilegedAction(
                "tenant-india",
                "EMERGENCY_ACCESS_GRANTED",
                "Emergency admin access granted",
                "alpha received temporary privileged access",
                "security.admin",
                "EMERGENCY_ACCESS",
                "grant-1",
                "ACTIVE",
                Map.of("grantId", "grant-1"),
                Map.of("severity", "CRITICAL"));

        verify(alertEmailNotificationService, times(2))
                .sendDirectEmail(anyString(), anyString(), any(), anyString(), anyString(), anyString());
        verify(alertWebhookNotificationService, times(2))
                .sendDirectWebhook(anyString(), anyString(), any(), anyString(), anyString(), any());
    }
}
