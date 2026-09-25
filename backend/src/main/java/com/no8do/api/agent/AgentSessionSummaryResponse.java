package com.no8do.api.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

public record AgentSessionSummaryResponse(
        UUID sessionId,
        String clientName,
        String clientVersion,
        UUID workspaceId,
        AgentTransport transport,
        String protocolName,
        int protocolVersion,
        AgentRuntimeMode runtimeMode,
        AgentPresenceStatus presenceStatus,
        Instant registeredAt,
        Instant lastSeenAt,
        Instant lastActivityAt,
        @JsonInclude(JsonInclude.Include.ALWAYS) Instant disconnectedAt,
        @JsonInclude(JsonInclude.Include.ALWAYS) Instant revokedAt) {

    static AgentSessionSummaryResponse from(AgentSession session, AgentPresenceStatus presenceStatus) {
        return new AgentSessionSummaryResponse(session.getId(), session.getClientName(), session.getClientVersion(),
                session.getWorkspaceId(), session.getTransport(), session.getProtocolName(), session.getProtocolVersion(),
                session.getRuntimeMode(), presenceStatus, session.getRegisteredAt(), session.getLastSeenAt(),
                session.getLastActivityAt(), session.getDisconnectedAt(), session.getRevokedAt());
    }
}
