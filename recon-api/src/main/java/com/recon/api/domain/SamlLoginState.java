package com.recon.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_saml_login_states", schema = "recon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SamlLoginState {

    @Id
    @Column(name = "relay_state_hash")
    private String relayStateHash;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
