package com.recon.api.repository;

import com.recon.api.domain.SystemEndpointRuntimeCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SystemEndpointRuntimeCatalogRepository extends JpaRepository<SystemEndpointRuntimeCatalog, UUID> {

    List<SystemEndpointRuntimeCatalog> findByActiveTrueOrderByDisplayOrderAsc();

    Optional<SystemEndpointRuntimeCatalog> findBySystemNameIgnoreCaseAndEndpointModeIgnoreCase(String systemName,
                                                                                                String endpointMode);
}
