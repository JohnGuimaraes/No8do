package com.no8do.api.agent;

import com.no8do.api.integration.IntegrationAuthorization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "agent_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentSession {
    @Id
    private UUID id;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "workspace_id", updatable = false)
    private UUID workspaceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", updatable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_credential_id", updatable = false)
    private AgentCredential agentCredential;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_authorization_id", updatable = false)
    private IntegrationAuthorization integrationAuthorization;

    @Column(name = "client_name", nullable = false, length = 255, updatable = false)
    private String clientName;

    @Column(name = "client_version", nullable = false, length = 255, updatable = false)
    private String clientVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private AgentTransport transport;

    @Enumerated(EnumType.STRING)
    @Column(name = "runtime_mode", nullable = false, length = 30)
    private AgentRuntimeMode runtimeMode = AgentRuntimeMode.FULL;

    @Column(name = "protocol_name", nullable = false, length = 160, updatable = false)
    private String protocolName;

    @Column(name = "protocol_version", nullable = false, updatable = false)
    private int protocolVersion;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by_user_id")
    private UUID revokedByUserId;

    @Column(name = "transport_session_fingerprint", nullable = false, length = 64, updatable = false)
    private String transportSessionFingerprint;

    AgentSession(UUID id, UUID userId, UUID workspaceId, AgentClientIdentity clientIdentity,
            AgentTransport transport, No8doAgentProtocol protocol, String fingerprint) {
        this(id, userId, workspaceId, null, null, clientIdentity, transport, protocol, fingerprint);
    }

    AgentSession(UUID id, UUID userId, UUID workspaceId, Agent agent, AgentCredential agentCredential,
            AgentClientIdentity clientIdentity,
            AgentTransport transport, No8doAgentProtocol protocol, String fingerprint) {
        this.id = id;
        this.userId = userId;
        this.workspaceId = workspaceId;
        this.agent = agent;
        this.agentCredential = agentCredential;
        this.clientName = clientIdentity.clientName();
        this.clientVersion = clientIdentity.clientVersion();
        this.transport = transport;
        this.runtimeMode = AgentRuntimeMode.FULL;
        this.protocolName = protocol.protocolName();
        this.protocolVersion = protocol.protocolVersion();
        this.transportSessionFingerprint = fingerprint;
    }

    void setRuntimeMode(AgentRuntimeMode runtimeMode) {
        this.runtimeMode = java.util.Objects.requireNonNull(runtimeMode, "runtimeMode");
    }

    UUID auditUserId() {
        return userId != null ? userId
                : integrationAuthorization == null ? null : integrationAuthorization.getAuthorizedByUserId();
    }

    boolean revoke(Instant revokedAt, UUID revokedByUserId) {
        if (this.revokedAt != null) return false;
        this.revokedAt = java.util.Objects.requireNonNull(revokedAt, "revokedAt");
        this.revokedByUserId = java.util.Objects.requireNonNull(revokedByUserId, "revokedByUserId");
        return true;
    }

    @PrePersist
    void prePersist() {
        if (registeredAt == null) registeredAt = Instant.now();
        if (lastSeenAt == null) lastSeenAt = registeredAt;
    }
}
