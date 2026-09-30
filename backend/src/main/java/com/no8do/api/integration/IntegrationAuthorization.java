package com.no8do.api.integration;

import com.no8do.api.agent.Agent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "integration_authorizations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IntegrationAuthorization {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "token_selector", nullable = false, length = 22, unique = true, updatable = false)
    private String tokenSelector;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false, updatable = false)
    private Agent agent;

    /** Historical grantor identity; intentionally has no destructive User FK. */
    @Column(name = "authorized_by_user_id", nullable = false, updatable = false)
    private UUID authorizedByUserId;

    @Column(name = "installation_id", nullable = false, updatable = false)
    private UUID installationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "host_type", nullable = false, length = 20, updatable = false)
    private IntegrationHostType hostType;

    @Column(name = "display_label", length = 120, updatable = false)
    private String displayLabel;

    @Column(name = "integration_version", nullable = false, length = 64, updatable = false)
    private String integrationVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private IntegrationAuthorizationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 80)
    private String revokeReason;

    IntegrationAuthorization(UUID id, String tokenSelector, String tokenHash, Agent agent,
            UUID authorizedByUserId, UUID installationId, IntegrationHostType hostType,
            String displayLabel, String integrationVersion, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.tokenSelector = tokenSelector;
        this.tokenHash = tokenHash;
        this.agent = agent;
        this.authorizedByUserId = authorizedByUserId;
        this.installationId = installationId;
        this.hostType = hostType;
        this.displayLabel = displayLabel;
        this.integrationVersion = integrationVersion;
        this.status = IntegrationAuthorizationStatus.ACTIVE;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    boolean revoke(Instant at, String reason) {
        // Expiry denies runtime access; a later explicit revoke still terminates historical sessions.
        if (status == IntegrationAuthorizationStatus.REVOKED) return false;
        status = IntegrationAuthorizationStatus.REVOKED;
        revokedAt = at;
        revokeReason = reason;
        return true;
    }

    boolean expire() {
        if (status != IntegrationAuthorizationStatus.ACTIVE) return false;
        status = IntegrationAuthorizationStatus.EXPIRED;
        revokedAt = expiresAt;
        revokeReason = "EXPIRED";
        return true;
    }

    void markUsed(Instant at) { lastUsedAt = at; }

    @Override public String toString() {
        return "IntegrationAuthorization[id=" + id + ", agentId="
                + (agent == null ? null : agent.getId()) + ", status=" + status + "]";
    }
}
