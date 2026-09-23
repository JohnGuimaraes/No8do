package com.no8do.api.agent;

import java.util.List;

public record AgentSessionContext(AgentSession session, List<AgentCapability> effectiveCapabilities) {
    public AgentSessionContext {
        effectiveCapabilities = List.copyOf(effectiveCapabilities);
    }
}
