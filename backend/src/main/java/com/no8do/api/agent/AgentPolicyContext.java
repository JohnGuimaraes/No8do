package com.no8do.api.agent;

import java.util.UUID;

public record AgentPolicyContext(AgentSession session, UUID requestedWorkspaceId, AgentCapability operation) {
    public AgentPolicyContext {
        if (session == null) throw new IllegalArgumentException("session é obrigatório.");
        if (requestedWorkspaceId == null) throw new IllegalArgumentException("requestedWorkspaceId é obrigatório.");
        if (operation == null) throw new IllegalArgumentException("operation é obrigatório.");
    }
}
