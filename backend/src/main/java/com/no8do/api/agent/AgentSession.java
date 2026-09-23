package com.no8do.api.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "workspace_id", updatable = false)
    private UUID workspaceId;

    @Column(name = "client_name", nullable = false, length = 255, updatable = false)
    private String clientName;

    @Column(name = "client_version", nullable = false, length = 255, updatable = false)
    private String clientVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private AgentTransport transport;

    @Column(name = "protocol_name", nullable = false, length = 160, updatable = false)
    private String protocolName;

    @Column(name = "protocol_version", nullable = false, updatable = false)
    private int protocolVersion;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(name = "transport_session_fingerprint", nullable = false, length = 64, updatable = false)
    private String transportSessionFingerprint;

    AgentSession(UUID id, UUID userId, UUID workspaceId, AgentClientIdentity clientIdentity,
            AgentTransport transport, No8doAgentProtocol protocol, String fingerprint) {
        this.id = id;
        this.userId = userId;
        this.workspaceId = workspaceId;
        this.clientName = clientIdentity.clientName();
        this.clientVersion = clientIdentity.clientVersion();
        this.transport = transport;
        this.protocolName = protocol.protocolName();
        this.protocolVersion = protocol.protocolVersion();
        this.transportSessionFingerprint = fingerprint;
    }

    @PrePersist
    void prePersist() {
        if (registeredAt == null) registeredAt = Instant.now();
    }
}
