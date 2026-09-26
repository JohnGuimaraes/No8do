package com.no8do.api.agent;

import java.time.Instant;
import java.util.UUID;

/** Public administrative projection; intentionally excludes creator and audit internals. */
public record AgentResponse(
        UUID id,
        UUID workspaceId,
        String name,
        String description,
        String providerDescriptor,
        AgentLifecycleStatus lifecycleStatus,
        Instant createdAt,
        Instant updatedAt
) {
    public static AgentResponse from(Agent agent) {
        return new AgentResponse(agent.getId(), agent.getWorkspace().getId(), agent.getName(), agent.getDescription(),
                agent.getProviderDescriptor(), agent.getLifecycleStatus(), agent.getCreatedAt(), agent.getUpdatedAt());
    }
}
