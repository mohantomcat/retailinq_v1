package com.recon.api.service;

import com.recon.api.domain.TenantConfig;
import com.recon.api.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;

    public TenantConfig getTenant(String tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseGet(() -> TenantConfig.builder()
                        .tenantId(tenantId)
                        .tenantName("Unknown")
                        .timezone("UTC")
                        .dateFormat("yyyy-MM-dd")
                        .dateDisplayFormat("dd-MMM-yyyy")
                        .build());
    }

    public List<TenantConfig> getAllTenants() {
        return tenantRepository.findAll();
    }
}