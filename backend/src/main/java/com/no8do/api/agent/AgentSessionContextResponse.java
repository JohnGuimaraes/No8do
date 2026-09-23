package com.no8do.api.agent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgentSessionContextResponse(
        UUID sessionId,
        String clientName,
        String clientVersion,
        UUID workspaceId,
        AgentTransport transport,
        String protocolName,
        int protocolVersion,
        AgentRuntimeMode runtimeMode,
        List<AgentCapability> effectiveCapabilities,
        List<AgentPolicy> policies,
        Instant registeredAt,
        AgentPresenceStatus presenceStatus,
        Instant lastSeenAt,
        Instant lastActivityAt) {
    public AgentSessionContextResponse {
        effectiveCapabilities = List.copyOf(effectiveCapabilities);
        policies = List.copyOf(policies);
    }

    static AgentSessionContextResponse from(AgentSessionContext context) {
        AgentSession session = context.session();
        return new AgentSessionContextResponse(session.getId(), session.getClientName(), session.getClientVersion(),
                session.getWorkspaceId(), session.getTransport(), session.getProtocolName(), session.getProtocolVersion(),
                session.getRuntimeMode(), context.effectiveCapabilities(), context.policies().policies(), session.getRegisteredAt(),
                context.presenceStatus(), context.lastSeenAt(), context.lastActivityAt());
    }
}
