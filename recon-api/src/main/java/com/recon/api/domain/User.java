package com.recon.api.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "identity_provider", nullable = false)
    @Builder.Default
    private String identityProvider = "LOCAL";

    @Column(name = "external_subject")
    private String externalSubject;

    @Column(name = "directory_external_id")
    private String directoryExternalId;

    @Column(name = "manager_user_id")
    private UUID managerUserId;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "access_review_status", nullable = false)
    @Builder.Default
    private String accessReviewStatus = "PENDING";

    @Column(name = "last_access_review_at")
    private LocalDateTime lastAccessReviewAt;

    @Column(name = "last_access_review_by")
    private String lastAccessReviewBy;

    @Column(name = "access_review_due_at")
    private LocalDateTime accessReviewDueAt;

    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_failed_login_at")
    private LocalDateTime lastFailedLoginAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            schema = "recon",
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            schema = "recon",
            name = "user_stores",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "store_id")
    @Builder.Default
    private Set<String> storeIds = new HashSet<>();

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        normalizeIdentityGovernanceDefaults();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
        normalizeIdentityGovernanceDefaults();
    }

    public Set<String> getAllPermissions() {
        Set<String> perms = new HashSet<>();
        for (Role role : roles) {
            for (Permission p : role.getPermissions()) {
                perms.add(p.getCode());
            }
        }
        return perms;
    }

    private void normalizeIdentityGovernanceDefaults() {
        if (identityProvider == null || identityProvider.isBlank()) {
            identityProvider = "LOCAL";
        }
        identityProvider = identityProvider.trim().toUpperCase();
        if (accessReviewStatus == null || accessReviewStatus.isBlank()) {
            accessReviewStatus = "PENDING";
        }
        accessReviewStatus = accessReviewStatus.trim().toUpperCase();
        if (accessReviewDueAt == null) {
            accessReviewDueAt = LocalDateTime.now().plusDays(90);
        }
    }
}
