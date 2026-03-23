package com.recon.api.repository;

import com.recon.api.domain.TenantBrandingConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantBrandingRepository extends JpaRepository<TenantBrandingConfigEntity, String> {
}
