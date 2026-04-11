package com.recon.api.service;

import com.recon.api.domain.GrantEmergencyAccessRequest;
import com.recon.api.domain.Permission;
import com.recon.api.domain.QuarterlyAccessReviewCycleResponse;
import com.recon.api.domain.Role;
import com.recon.api.domain.User;
import com.recon.api.domain.UserEmergencyAccessGrant;
import com.recon.api.repository.AuditLedgerEntryRepository;
import com.recon.api.repository.RoleRepository;
import com.recon.api.repository.UserEmergencyAccessGrantRepository;
import com.recon.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivilegedAccessServiceTest {

    @Mock
    private UserEmergencyAccessGrantRepository userEmergencyAccessGrantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AuditLedgerEntryRepository auditLedgerEntryRepository;

    @Mock
    private AuditLedgerService auditLedgerService;

    @Mock
    private AccessGovernanceNotificationService accessGovernanceNotificationService;

    private PrivilegedAccessService privilegedAccessService;

    @BeforeEach
    void setUp() {
        privilegedAccessService = new PrivilegedAccessService(
                userEmergencyAccessGrantRepository,
                userRepository,
                roleRepository,
                auditLedgerEntryRepository,
                auditLedgerService,
                accessGovernanceNotificationService);
    }

    @Test
    void grantEmergencyAccessAddsEffectivePrivilegedPermissions() {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID roleId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID grantId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        User user = User.builder()
                .id(userId)
                .tenantId("tenant-india")
                .username("alpha")
                .active(true)
                .roles(Set.of())
                .build();
        Role privilegedRole = Role.builder()
                .id(roleId)
                .tenantId("tenant-india")
                .name("Emergency Admin")
                .permissions(Set.of(Permission.builder().code("ADMIN_USERS").build()))
                .build();

        when(userRepository.findByIdAndTenantId(userId, "tenant-india"))
                .thenReturn(Optional.of(user));
        when(roleRepository.findAllById(Set.of(roleId)))
                .thenReturn(List.of(privilegedRole));
        when(userEmergencyAccessGrantRepository.save(any(UserEmergencyAccessGrant.class)))
                .thenAnswer(invocation -> {
                    UserEmergencyAccessGrant grant = invocation.getArgument(0, UserEmergencyAccessGrant.class);
                    grant.setId(grantId);
                    grant.setGrantedAt(LocalDateTime.of(2026, 4, 11, 9, 0));
                    return grant;
                });
        when(userEmergencyAccessGrantRepository
                .findByTenantIdAndUser_IdAndRevokedAtIsNullAndExpiresAtBeforeOrderByExpiresAtAsc(
                        eq("tenant-india"),
                        eq(userId),
                        any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(userEmergencyAccessGrantRepository
                .findByTenantIdAndUser_IdAndRevokedAtIsNullAndExpiresAtAfterOrderByExpiresAtAsc(
                        eq("tenant-india"),
                        eq(userId),
                        any(LocalDateTime.class)))
                .thenReturn(List.of(UserEmergencyAccessGrant.builder()
                        .id(grantId)
                        .tenantId("tenant-india")
                        .user(user)
                        .roles(Set.of(privilegedRole))
                        .justification("P1 incident")
                        .approvalNote("Approved by duty manager")
                        .grantedBy("security.admin")
                        .grantedAt(LocalDateTime.of(2026, 4, 11, 9, 0))
                        .expiresAt(LocalDateTime.now().plusHours(4))
                        .build()));

        var grant = privilegedAccessService.grantEmergencyAccess(
                "tenant-india",
                buildGrantRequest(userId, roleId),
                "security.admin");
        var resolvedAccess = privilegedAccessService.resolveEffectiveAccess(user);

        assertTrue(grant.isActive());
        assertEquals("alpha", grant.getUsername());
        assertTrue(resolvedAccess.emergencyAccessActive());
        assertTrue(resolvedAccess.effectivePermissions().contains("ADMIN_USERS"));
        assertEquals(1, resolvedAccess.emergencyRoles().size());
        verify(auditLedgerService).record(any());
    }

    @Test
    void startQuarterlyReviewCycleQueuesDueUsersAndCountsMissingManagers() {
        User dueUser = User.builder()
                .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .tenantId("tenant-india")
                .username("due-user")
                .active(true)
                .accessReviewStatus("CERTIFIED")
                .accessReviewDueAt(LocalDateTime.now().minusDays(1))
                .build();
        User alreadyQueued = User.builder()
                .id(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .tenantId("tenant-india")
                .username("queued-user")
                .active(true)
                .accessReviewStatus("PENDING_MANAGER")
                .accessReviewDueAt(LocalDateTime.now().minusDays(3))
                .build();
        User futureUser = User.builder()
                .id(UUID.fromString("33333333-3333-3333-3333-333333333333"))
                .tenantId("tenant-india")
                .username("future-user")
                .active(true)
                .accessReviewStatus("CERTIFIED")
                .accessReviewDueAt(LocalDateTime.now().plusDays(10))
                .build();

        when(userRepository.findByTenantId("tenant-india"))
                .thenReturn(List.of(dueUser, alreadyQueued, futureUser));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, User.class));

        QuarterlyAccessReviewCycleResponse response =
                privilegedAccessService.startQuarterlyAccessReviewCycle("tenant-india", "ops.manager");

        assertEquals(1, response.getQueuedUsers());
        assertEquals(1, response.getAlreadyQueuedUsers());
        assertEquals(2, response.getUsersMissingManager());
        assertEquals("PENDING_MANAGER", dueUser.getAccessReviewStatus());
        verify(auditLedgerService).record(any());
        verify(accessGovernanceNotificationService)
                .dispatchManagerAccessReviewRemindersForTenant("tenant-india", true);
    }

    private GrantEmergencyAccessRequest buildGrantRequest(UUID userId, UUID roleId) {
        GrantEmergencyAccessRequest request = new GrantEmergencyAccessRequest();
        request.setUserId(userId);
        request.setRoleIds(Set.of(roleId));
        request.setExpiresInHours(4);
        request.setJustification("P1 incident");
        request.setApprovalNote("Approved by duty manager");
        return request;
    }
}
