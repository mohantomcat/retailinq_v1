package com.recon.api.service;

import com.recon.api.domain.TenantAuthConfigEntity;
import com.recon.api.repository.TenantAuthConfigRepository;
import com.recon.api.security.ReconUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScimAuthenticationServiceTest {

    private static final String TOKEN_REF = "TEST_SCIM_TOKEN";

    @Mock
    private TenantAuthConfigRepository tenantAuthConfigRepository;

    @InjectMocks
    private ScimAuthenticationService scimAuthenticationService;

    @AfterEach
    void clearTokenRef() {
        System.clearProperty(TOKEN_REF);
    }

    @Test
    void authenticateReturnsScimPrincipalWhenBearerTokenMatches() {
        TenantAuthConfigEntity config = TenantAuthConfigEntity.builder()
                .tenantId("tenant-india")
                .scimEnabled(true)
                .scimBearerTokenRef(TOKEN_REF)
                .build();
        when(tenantAuthConfigRepository.findById("tenant-india"))
                .thenReturn(Optional.of(config));
        System.setProperty(TOKEN_REF, "super-secret-token");

        ReconUserPrincipal principal = scimAuthenticationService.authenticate(
                "tenant-india",
                "super-secret-token");

        assertEquals("tenant-india", principal.getTenantId());
        assertEquals("SCIM", principal.getAuthMode());
        assertTrue(principal.hasPermission("SCIM_PROVISION"));
    }

    @Test
    void authenticateRejectsInvalidBearerToken() {
        TenantAuthConfigEntity config = TenantAuthConfigEntity.builder()
                .tenantId("tenant-india")
                .scimEnabled(true)
                .scimBearerTokenRef(TOKEN_REF)
                .build();
        when(tenantAuthConfigRepository.findById("tenant-india"))
                .thenReturn(Optional.of(config));
        System.setProperty(TOKEN_REF, "super-secret-token");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> scimAuthenticationService.authenticate("tenant-india", "wrong-token"));

        assertEquals("SCIM bearer token is invalid", error.getMessage());
    }
}
