package com.no8do.api.agent;

import java.util.List;

public record AgentSessionContext(AgentSession session, List<AgentCapability> effectiveCapabilities,
        AgentPolicyManifest policies) {
    public AgentSessionContext {
        effectiveCapabilities = List.copyOf(effectiveCapabilities);
        if (policies == null) throw new IllegalArgumentException("policies é obrigatório.");
    }
}
