package com.recon.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user_emergency_access_grants", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEmergencyAccessGrant {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            schema = "recon",
            name = "user_emergency_access_grant_roles",
            joinColumns = @JoinColumn(name = "grant_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new LinkedHashSet<>();

    @Column(name = "justification", nullable = false)
    private String justification;

    @Column(name = "approval_note", nullable = false)
    private String approvalNote;

    @Column(name = "granted_by", nullable = false)
    private String grantedBy;

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by")
    private String revokedBy;

    @Column(name = "revoke_note")
    private String revokeNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (grantedAt == null) {
            grantedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
