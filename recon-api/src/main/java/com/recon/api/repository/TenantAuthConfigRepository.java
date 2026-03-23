package com.recon.api.repository;

import com.recon.api.domain.TenantAuthConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantAuthConfigRepository extends JpaRepository<TenantAuthConfigEntity, String> {
}
