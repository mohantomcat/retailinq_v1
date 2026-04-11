package com.recon.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.api.domain.Role;
import com.recon.api.domain.ScimGroupResource;
import com.recon.api.domain.TenantAuthConfigEntity;
import com.recon.api.domain.TenantScimGroup;
import com.recon.api.domain.User;
import com.recon.api.domain.UserScimGroupMembership;
import com.recon.api.repository.TenantAuthConfigRepository;
import com.recon.api.repository.TenantScimGroupRepository;
import com.recon.api.repository.UserRepository;
import com.recon.api.repository.UserScimGroupMembershipRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScimGroupPushServiceTest {

    @Mock
    private TenantAuthConfigRepository tenantAuthConfigRepository;

    @Mock
    private TenantScimGroupRepository tenantScimGroupRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserScimGroupMembershipRepository userScimGroupMembershipRepository;

    @Mock
    private EnterpriseIdentityLifecycleService enterpriseIdentityLifecycleService;

    @Mock
    private AuditLedgerService auditLedgerService;

    private ScimGroupPushService scimGroupPushService;

    @BeforeEach
    void setUp() {
        scimGroupPushService = new ScimGroupPushService(
                tenantAuthConfigRepository,
                tenantScimGroupRepository,
                userRepository,
                userScimGroupMembershipRepository,
                enterpriseIdentityLifecycleService,
                auditLedgerService,
                new ObjectMapper());
    }

    @Test
    void listGroupsBuildsStableLocationAndMembers() {
        UUID groupId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        when(tenantAuthConfigRepository.findById("tenant-india"))
                .thenReturn(Optional.of(TenantAuthConfigEntity.builder()
                        .tenantId("tenant-india")
                        .scimEnabled(true)
                        .scimGroupPushEnabled(true)
                        .build()));
        when(tenantScimGroupRepository.findByTenantIdOrderByDisplayNameAsc("tenant-india"))
                .thenReturn(List.of(TenantScimGroup.builder()
                        .id(groupId)
                        .tenantId("tenant-india")
                        .displayName("Retail HQ")
                        .createdAt(LocalDateTime.of(2026, 4, 10, 0, 0))
                        .updatedAt(LocalDateTime.of(2026, 4, 10, 1, 0))
                        .build()));
        when(userScimGroupMembershipRepository.findByTenantId("tenant-india"))
                .thenReturn(List.of(UserScimGroupMembership.builder()
                        .tenantId("tenant-india")
                        .externalGroup("Retail HQ")
                        .user(User.builder()
                                .id(userId)
                                .tenantId("tenant-india")
                                .username("alpha")
                                .fullName("Alpha User")
                                .build())
                        .updatedAt(LocalDateTime.of(2026, 4, 10, 2, 0))
                        .build()));

        var response = scimGroupPushService.listGroups("tenant-india", null, 1, 50);

        assertEquals(1, response.getTotalResults());
        ScimGroupResource resource = response.getResources().get(0);
        assertEquals(groupId.toString(), resource.getId());
        assertEquals("Retail HQ", resource.getDisplayName());
        assertEquals(userId.toString(), resource.getMembers().get(0).getValue());
        assertEquals("/api/scim/v2/tenant-india/Groups/" + groupId, resource.getMeta().getLocation());
    }

    @Test
    void deprovisionUserWithRemoveAccessClearsRolesAndMemberships() {
        UUID userId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        User user = User.builder()
                .id(userId)
                .tenantId("tenant-india")
                .username("beta")
                .active(true)
                .roles(Set.of(Role.builder()
                        .id(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"))
                        .tenantId("tenant-india")
                        .name("Supervisor")
                        .build()))
                .build();
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0, User.class));

        User saved = scimGroupPushService.deprovisionUser(
                TenantAuthConfigEntity.builder()
                        .tenantId("tenant-india")
                        .scimEnabled(true)
                        .scimDeprovisionPolicy("REMOVE_ACCESS")
                        .build(),
                user,
                "scim",
                "SCIM_USER_DELETE",
                true);

        assertFalse(saved.isActive());
        verify(userScimGroupMembershipRepository).deleteByTenantIdAndUser_Id("tenant-india", userId);
        verify(enterpriseIdentityLifecycleService).applyMappedRoles(
                "tenant-india",
                user,
                Set.of(),
                Set.of(),
                "SCIM",
                "scim");
    }
}
