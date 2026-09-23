package com.no8do.api.agent;

import java.time.Instant;
import java.util.List;

public record AgentSessionContext(AgentSession session, List<AgentCapability> effectiveCapabilities,
        AgentPolicyManifest policies, AgentPresenceStatus presenceStatus, Instant lastSeenAt, Instant lastActivityAt,
        Instant disconnectedAt) {
    public AgentSessionContext(AgentSession session, List<AgentCapability> effectiveCapabilities,
            AgentPolicyManifest policies) {
        this(session, effectiveCapabilities, policies,
                session.getDisconnectedAt() == null ? AgentPresenceStatus.CONNECTED : AgentPresenceStatus.DISCONNECTED,
                session.getLastSeenAt(), session.getLastActivityAt(), session.getDisconnectedAt());
    }

    public AgentSessionContext {
        effectiveCapabilities = List.copyOf(effectiveCapabilities);
        if (policies == null) throw new IllegalArgumentException("policies é obrigatório.");
    }
}
