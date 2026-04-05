package com.recon.api.repository;

import com.recon.api.domain.ReconModuleCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconModuleCatalogRepository extends JpaRepository<ReconModuleCatalog, UUID> {

    List<ReconModuleCatalog> findByActiveTrueOrderByDisplayOrderAsc();

    Optional<ReconModuleCatalog> findByReconViewIgnoreCase(String reconView);

    Optional<ReconModuleCatalog> findByTabIdIgnoreCase(String tabId);
}
