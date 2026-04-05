package com.recon.api.repository;

import com.recon.api.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository
        extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByCode(String code);

    Optional<Permission> findByCodeIgnoreCase(String code);

    List<Permission> findByModule(String module);
}
