package com.no8do.api.agent;

import java.time.Instant;
import java.util.UUID;

public record AgentSessionResponse(
        UUID sessionId,
        String clientName,
        String clientVersion,
        UUID workspaceId,
        AgentTransport transport,
        AgentRuntimeMode runtimeMode,
        String protocolName,
        int protocolVersion,
        Instant registeredAt
) {
    static AgentSessionResponse from(AgentSession session) {
        return new AgentSessionResponse(session.getId(), session.getClientName(), session.getClientVersion(),
                session.getWorkspaceId(), session.getTransport(), session.getRuntimeMode(), session.getProtocolName(),
                session.getProtocolVersion(), session.getRegisteredAt());
    }
}
