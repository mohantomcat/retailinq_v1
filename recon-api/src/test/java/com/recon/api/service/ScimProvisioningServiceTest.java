package com.recon.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.api.domain.ScimListResponse;
import com.recon.api.domain.ScimUserResource;
import com.recon.api.domain.TenantAuthConfigEntity;
import com.recon.api.domain.User;
import com.recon.api.repository.TenantAuthConfigRepository;
import com.recon.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScimProvisioningServiceTest {

    @Mock
    private TenantAuthConfigRepository tenantAuthConfigRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EnterpriseIdentityLifecycleService enterpriseIdentityLifecycleService;

    @Mock
    private AuditLedgerService auditLedgerService;

    private ScimProvisioningService scimProvisioningService;

    @BeforeEach
    void setUp() {
        scimProvisioningService = new ScimProvisioningService(
                tenantAuthConfigRepository,
                userRepository,
                enterpriseIdentityLifecycleService,
                auditLedgerService,
                new ObjectMapper());
    }

    @Test
    void listUsersFiltersByExternalIdAndBuildsScimLocation() {
        when(tenantAuthConfigRepository.findById("tenant-india"))
                .thenReturn(Optional.of(TenantAuthConfigEntity.builder()
                        .tenantId("tenant-india")
                        .scimEnabled(true)
                        .build()));
        when(userRepository.findByTenantId("tenant-india"))
                .thenReturn(List.of(
                        User.builder()
                                .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                                .tenantId("tenant-india")
                                .username("alpha")
                                .email("alpha@example.com")
                                .fullName("Alpha User")
                                .directoryExternalId("ext-1")
                                .createdAt(LocalDateTime.of(2026, 4, 1, 0, 0))
                                .active(true)
                                .build(),
                        User.builder()
                                .id(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                                .tenantId("tenant-india")
                                .username("beta")
                                .email("beta@example.com")
                                .fullName("Beta User")
                                .directoryExternalId("ext-2")
                                .createdAt(LocalDateTime.of(2026, 4, 2, 0, 0))
                                .active(false)
                                .build()));

        ScimListResponse<ScimUserResource> response = scimProvisioningService.listUsers(
                "tenant-india",
                "externalId eq \"ext-2\"",
                1,
                50);

        assertEquals(1, response.getTotalResults());
        assertEquals(1, response.getResources().size());
        assertEquals("beta", response.getResources().get(0).getUserName());
        assertEquals("ext-2", response.getResources().get(0).getExternalId());
        assertTrue(response.getResources().get(0).getMeta().getLocation().endsWith(
                "/api/scim/v2/tenant-india/Users/22222222-2222-2222-2222-222222222222"));
    }
}
