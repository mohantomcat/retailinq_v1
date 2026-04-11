package com.recon.api.service;

import com.recon.api.domain.AccessScopeSummaryDto;
import com.recon.api.domain.ReviewUserAccessRequest;
import com.recon.api.domain.User;
import com.recon.api.repository.RoleRepository;
import com.recon.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLedgerService auditLedgerService;

    @Mock
    private AccessScopeService accessScopeService;

    @Mock
    private ReconModuleService reconModuleService;

    @Mock
    private PrivilegedAccessService privilegedAccessService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                roleRepository,
                passwordEncoder,
                auditLedgerService,
                accessScopeService,
                reconModuleService,
                privilegedAccessService);
    }

    @Test
    void reviewUserAccessRequiresReviewNote() {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(userRepository.findByIdAndTenantId(userId, "tenant-india"))
                .thenReturn(Optional.of(User.builder()
                        .id(userId)
                        .tenantId("tenant-india")
                        .username("alpha")
                        .active(true)
                        .build()));

        ReviewUserAccessRequest request = new ReviewUserAccessRequest();
        request.setDecision("CERTIFIED");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                userService.reviewUserAccess(
                        "tenant-india",
                        userId,
                        request,
                        "security.admin",
                        Set.of("ACCESS_REVIEW_MANAGE")));

        assertEquals("Review note is required", ex.getMessage());
    }

    @Test
    void assignedManagerCanReviewWithoutPrivilegedPermission() {
        UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID managerId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        User targetUser = User.builder()
                .id(userId)
                .tenantId("tenant-india")
                .username("associate")
                .fullName("Associate User")
                .managerUserId(managerId)
                .accessReviewStatus("PENDING_MANAGER")
                .accessReviewDueAt(LocalDateTime.now().minusDays(1))
                .roles(Set.of())
                .storeIds(Set.of())
                .active(true)
                .build();
        User manager = User.builder()
                .id(managerId)
                .tenantId("tenant-india")
                .username("store.manager")
                .fullName("Store Manager")
                .active(true)
                .roles(Set.of())
                .storeIds(Set.of())
                .build();

        when(userRepository.findByIdAndTenantId(userId, "tenant-india"))
                .thenReturn(Optional.of(targetUser));
        when(userRepository.findByIdAndTenantId(managerId, "tenant-india"))
                .thenReturn(Optional.of(manager));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, User.class));
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(targetUser));
        when(accessScopeService.summarizeUserScope(targetUser))
                .thenReturn(AccessScopeSummaryDto.builder()
                        .allStoreAccess(true)
                        .directStoreIds(List.of())
                        .effectiveStoreIds(List.of())
                        .organizationScopes(List.of())
                        .accessLabel("All stores")
                        .build());
        when(privilegedAccessService.resolveEffectiveAccess(targetUser))
                .thenReturn(new PrivilegedAccessService.ResolvedAccess(
                        Set.of(),
                        Set.of(),
                        List.of(),
                        false,
                        null));
        when(reconModuleService.getAccessibleModules("tenant-india", Set.of()))
                .thenReturn(List.of());

        ReviewUserAccessRequest request = new ReviewUserAccessRequest();
        request.setDecision("CERTIFIED");
        request.setNotes("Store manager approved quarterly review");

        var response = userService.reviewUserAccess(
                "tenant-india",
                userId,
                request,
                "store.manager",
                Set.of());

        assertEquals("CERTIFIED", response.getAccessReviewStatus());
        assertEquals("store.manager", response.getLastAccessReviewBy());
        assertEquals("store.manager", response.getManagerUsername());
        verify(auditLedgerService).record(any());
    }
}
