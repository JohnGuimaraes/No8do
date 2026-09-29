package com.no8do.api.agent;

import com.no8do.api.replay.ReplayUsageResult;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class AgentEventFactory {
    private static final AgentEventMetadata.Empty EMPTY = new AgentEventMetadata.Empty();
    private static final String SAFE_POLICY_DENIAL_REASON = "Uma policy ENFORCED negou a operação.";

    private final Clock clock;

    public AgentEventFactory(Clock clock) {
        this.clock = clock;
    }

    public AgentEvent connected(AgentSession session) {
        return create(AgentEventType.AGENT_CONNECTED, session, EMPTY);
    }

    public AgentEvent disconnected(AgentSession session) {
        return create(AgentEventType.AGENT_DISCONNECTED, session, EMPTY);
    }

    public AgentEvent runtimeModeChanged(AgentSession session, AgentRuntimeMode previousMode,
            AgentRuntimeMode newMode) {
        return create(AgentEventType.RUNTIME_MODE_CHANGED, session,
                new AgentEventMetadata.RuntimeModeChanged(previousMode, newMode));
    }

    public AgentEvent capabilityDenied(AgentSession session, AgentCapability requiredCapability) {
        return create(AgentEventType.CAPABILITY_DENIED, session,
                new AgentEventMetadata.CapabilityDenied(requiredCapability, session.getRuntimeMode()));
    }

    public AgentEvent policyDenied(AgentSession session, AgentPolicyDecision decision) {
        return create(AgentEventType.POLICY_DENIED, session,
                new AgentEventMetadata.PolicyDenied(decision.policyId(), SAFE_POLICY_DENIAL_REASON));
    }

    public AgentEvent replayUsageRecorded(AgentSession session, UUID replayId, int replayVersion,
            ReplayUsageResult result) {
        return create(AgentEventType.REPLAY_USAGE_RECORDED, session,
                new AgentEventMetadata.ReplayUsageRecorded(replayId, replayVersion, result));
    }

    public AgentEvent sessionRevoked(AgentSession session) {
        if (session == null || session.getRevokedAt() == null || session.getRevokedByUserId() == null) {
            throw new IllegalArgumentException("Sessão revogada é obrigatória para emitir o evento.");
        }
        var occurredAt = session.getRevokedAt();
        AgentEventMetadata.SessionRevoked metadata = new AgentEventMetadata.SessionRevoked(session.getId(),
                session.getRevokedByUserId(), session.getWorkspaceId(), occurredAt);
        return new AgentEvent(AgentSessionRevocationEventId.forSession(session.getId()),
                AgentEventType.AGENT_SESSION_REVOKED, session.getId(), session.getUserId(),
                session.getWorkspaceId(), occurredAt, metadata);
    }

    public AgentEvent operationalContextChanged(AgentSession session, long version,
            java.util.List<String> changedFields, OperationalContextResolutionStatus projectStatus,
            OperationalContextResolutionStatus workItemStatus) {
        return create(AgentEventType.AGENT_OPERATIONAL_CONTEXT_CHANGED, session,
                new AgentEventMetadata.OperationalContextChanged(version, changedFields, projectStatus, workItemStatus));
    }

    private AgentEvent create(AgentEventType type, AgentSession session, AgentEventMetadata metadata) {
        if (session == null) throw new IllegalArgumentException("AgentSession é obrigatória para emitir eventos.");
        return new AgentEvent(UUID.randomUUID(), type, session.getId(), session.getUserId(),
                session.getWorkspaceId(), clock.instant(), metadata);
    }
}
