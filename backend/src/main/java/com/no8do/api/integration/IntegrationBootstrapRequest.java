package com.no8do.api.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "integration_bootstrap_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IntegrationBootstrapRequest {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "device_code_hash", nullable = false, length = 64, unique = true, updatable = false)
    private String deviceCodeHash;

    @Column(name = "user_code_hmac", nullable = false, length = 64, unique = true, updatable = false)
    private String userCodeHmac;

    @Column(name = "pkce_challenge", nullable = false, length = 43, updatable = false)
    private String pkceChallenge;

    @Column(name = "pkce_method", nullable = false, length = 4, updatable = false)
    private String pkceMethod;

    @Column(name = "installation_id", nullable = false, updatable = false)
    private UUID installationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "host_type", nullable = false, length = 20, updatable = false)
    private IntegrationHostType hostType;

    @Column(name = "integration_version", nullable = false, length = 64, updatable = false)
    private String integrationVersion;

    @Column(name = "display_label", length = 120, updatable = false)
    private String displayLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private IntegrationBootstrapState state;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "last_poll_at")
    private Instant lastPollAt;

    @Column(name = "poll_interval_seconds", nullable = false)
    private int pollIntervalSeconds;

    @Column(name = "poll_count", nullable = false)
    private int pollCount;

    @Column(name = "authorized_by_user_id")
    private UUID authorizedByUserId;

    @Column(name = "approved_workspace_id")
    private UUID approvedWorkspaceId;

    @Column(name = "approved_agent_id")
    private UUID approvedAgentId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "denied_at")
    private Instant deniedAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    IntegrationBootstrapRequest(UUID id, String deviceCodeHash, String userCodeHmac, String pkceChallenge,
            String pkceMethod, UUID installationId, IntegrationHostType hostType, String integrationVersion,
            String displayLabel, Instant createdAt, Instant expiresAt, int initialPollIntervalSeconds) {
        this.id = id;
        this.deviceCodeHash = deviceCodeHash;
        this.userCodeHmac = userCodeHmac;
        this.pkceChallenge = pkceChallenge;
        this.pkceMethod = pkceMethod;
        this.installationId = installationId;
        this.hostType = hostType;
        this.integrationVersion = integrationVersion;
        this.displayLabel = displayLabel;
        this.state = IntegrationBootstrapState.PENDING;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.pollIntervalSeconds = initialPollIntervalSeconds;
    }

    void approve(UUID actorUserId, UUID workspaceId, UUID agentId, Instant at) {
        state = IntegrationBootstrapState.APPROVED;
        authorizedByUserId = actorUserId;
        approvedWorkspaceId = workspaceId;
        approvedAgentId = agentId;
        approvedAt = at;
    }

    void deny(Instant at) {
        state = IntegrationBootstrapState.DENIED;
        deniedAt = at;
    }

    void consume(Instant at) {
        state = IntegrationBootstrapState.CONSUMED;
        consumedAt = at;
    }

    void expire() {
        if (state == IntegrationBootstrapState.PENDING || state == IntegrationBootstrapState.APPROVED) {
            state = IntegrationBootstrapState.EXPIRED;
        }
    }

    void recordPoll(Instant at) {
        lastPollAt = at;
        pollCount++;
    }

    int slowDown(Instant at) {
        pollCount++;
        pollIntervalSeconds = Math.min(60, pollIntervalSeconds + 5);
        lastPollAt = at;
        return pollIntervalSeconds;
    }

    IntegrationBootstrapState effectiveState(Instant now) {
        if ((state == IntegrationBootstrapState.PENDING || state == IntegrationBootstrapState.APPROVED)
                && !now.isBefore(expiresAt)) return IntegrationBootstrapState.EXPIRED;
        return state;
    }

    @Override public String toString() {
        return "IntegrationBootstrapRequest[id=" + id + ", state=" + state + "]";
    }
}
