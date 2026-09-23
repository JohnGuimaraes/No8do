package com.no8do.api.agent;

import java.util.UUID;
import com.no8do.api.replay.ReplayStatus;
import com.no8do.api.replay.ReplayValidationEvidence;

public record AgentPolicyContext(AgentSession session, UUID requestedWorkspaceId, AgentCapability operation,
        ReplayStatus replayStatus, ReplayValidationEvidence validationEvidence) {
    public AgentPolicyContext(AgentSession session, UUID requestedWorkspaceId, AgentCapability operation) {
        this(session, requestedWorkspaceId, operation, null, null);
    }

    public AgentPolicyContext {
        if (requestedWorkspaceId == null) throw new IllegalArgumentException("requestedWorkspaceId é obrigatório.");
        if (operation == null) throw new IllegalArgumentException("operation é obrigatório.");
    }
}
