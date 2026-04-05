package com.recon.api.repository;

import com.recon.api.domain.ReconGroupCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconGroupCatalogRepository extends JpaRepository<ReconGroupCatalog, UUID> {

    List<ReconGroupCatalog> findByActiveTrueOrderByDisplayOrderAsc();

    Optional<ReconGroupCatalog> findByGroupCodeIgnoreCase(String groupCode);
}
