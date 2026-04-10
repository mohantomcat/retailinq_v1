package com.recon.api.repository;

import com.recon.api.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository
        extends JpaRepository<User, UUID> {

    Optional<User> findByUsernameAndTenantId(
            String username, String tenantId);

    Optional<User> findByUsername(String username);

    Optional<User> findByIdAndTenantId(UUID id, String tenantId);

    Optional<User> findByTenantIdAndEmailIgnoreCase(String tenantId, String email);

    Optional<User> findByTenantIdAndIdentityProviderIgnoreCaseAndExternalSubjectIgnoreCase(
            String tenantId, String identityProvider, String externalSubject);

    List<User> findByTenantId(String tenantId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByTenantIdAndUsernameIgnoreCase(String tenantId, String username);

    boolean existsByTenantIdAndEmailIgnoreCase(String tenantId, String email);

    @Query("""
            select distinct storeId
            from User u
            join u.storeIds storeId
            where u.tenantId = :tenantId
            """)
    List<String> findDistinctStoreIdsByTenantId(String tenantId);
}
