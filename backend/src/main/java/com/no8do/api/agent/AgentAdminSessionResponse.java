package com.no8do.api.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/** Operational session fields for workspace administrators, excluding owner identity and credentials. */
public record AgentAdminSessionResponse(
        UUID sessionId,
        String clientName,
        String clientVersion,
        AgentTransport transport,
        UUID workspaceId,
        AgentRuntimeMode runtimeMode,
        AgentPresenceStatus presenceStatus,
        Instant createdAt,
        Instant lastSeenAt,
        Instant lastActivityAt,
        @JsonInclude(JsonInclude.Include.ALWAYS) Instant disconnectedAt,
        @JsonInclude(JsonInclude.Include.ALWAYS) Instant revokedAt) {

    static AgentAdminSessionResponse from(AgentSession session, AgentPresenceStatus presenceStatus) {
        return new AgentAdminSessionResponse(session.getId(), session.getClientName(), session.getClientVersion(),
                session.getTransport(), session.getWorkspaceId(), session.getRuntimeMode(), presenceStatus,
                session.getRegisteredAt(), session.getLastSeenAt(), session.getLastActivityAt(),
                session.getDisconnectedAt(), session.getRevokedAt());
    }
}
