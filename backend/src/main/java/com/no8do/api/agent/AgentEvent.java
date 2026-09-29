package com.no8do.api.agent;

import java.time.Instant;
import java.util.UUID;

public record AgentEvent(UUID eventId, AgentEventType type, UUID sessionId, UUID userId,
        UUID workspaceId, Instant occurredAt, AgentEventMetadata metadata) {
    public AgentEvent {
        if (eventId == null || type == null || sessionId == null || userId == null
                || occurredAt == null || metadata == null) {
            throw new IllegalArgumentException("Campos comuns do evento de AgentSession são obrigatórios.");
        }
        boolean metadataMatches = switch (type) {
            case AGENT_CONNECTED, AGENT_DISCONNECTED -> metadata instanceof AgentEventMetadata.Empty;
            case RUNTIME_MODE_CHANGED -> metadata instanceof AgentEventMetadata.RuntimeModeChanged;
            case CAPABILITY_DENIED -> metadata instanceof AgentEventMetadata.CapabilityDenied;
            case POLICY_DENIED -> metadata instanceof AgentEventMetadata.PolicyDenied;
            case REPLAY_USAGE_RECORDED -> metadata instanceof AgentEventMetadata.ReplayUsageRecorded;
            case AGENT_SESSION_REVOKED -> metadata instanceof AgentEventMetadata.SessionRevoked;
            case AGENT_OPERATIONAL_CONTEXT_CHANGED -> metadata instanceof AgentEventMetadata.OperationalContextChanged;
        };
        if (!metadataMatches) {
            throw new IllegalArgumentException("Metadata incompatível com o tipo do evento.");
        }
    }
}
