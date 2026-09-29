package com.no8do.api.agent;

import com.no8do.api.replay.ReplayUsageResult;
import java.time.Instant;
import java.util.UUID;

/** Safe event metadata projection for public transports such as SSE. */
public sealed interface AgentEventPublicMetadata permits AgentEventPublicMetadata.Empty,
        AgentEventPublicMetadata.RuntimeModeChanged, AgentEventPublicMetadata.CapabilityDenied,
        AgentEventPublicMetadata.PolicyDenied, AgentEventPublicMetadata.ReplayUsageRecorded,
        AgentEventPublicMetadata.OperationalContextChanged,
        AgentEventPublicMetadata.SessionRevoked {

    static AgentEventPublicMetadata from(AgentEventMetadata metadata) {
        return switch (metadata) {
            case AgentEventMetadata.Empty ignored -> new Empty();
            case AgentEventMetadata.RuntimeModeChanged value ->
                    new RuntimeModeChanged(value.previousMode(), value.newMode());
            case AgentEventMetadata.CapabilityDenied value ->
                    new CapabilityDenied(value.requiredCapability(), value.runtimeMode());
            case AgentEventMetadata.PolicyDenied value -> new PolicyDenied(value.policyId(), value.reason());
            case AgentEventMetadata.ReplayUsageRecorded value ->
                    new ReplayUsageRecorded(value.replayId(), value.replayVersion(), value.result());
            case AgentEventMetadata.SessionRevoked value ->
                    new SessionRevoked(value.targetSessionId(), value.workspaceId(), value.occurredAt());
            case AgentEventMetadata.SessionBound ignored -> new Empty();
            case AgentEventMetadata.OperationalContextChanged value -> new OperationalContextChanged(
                    value.version(), value.changedFields(), value.projectResolutionStatus(),
                    value.workItemResolutionStatus());
        };
    }

    record Empty() implements AgentEventPublicMetadata {}

    record RuntimeModeChanged(AgentRuntimeMode previousMode, AgentRuntimeMode newMode)
            implements AgentEventPublicMetadata {}

    record CapabilityDenied(AgentCapability requiredCapability, AgentRuntimeMode runtimeMode)
            implements AgentEventPublicMetadata {}

    record PolicyDenied(String policyId, String reason) implements AgentEventPublicMetadata {}

    record ReplayUsageRecorded(UUID replayId, int replayVersion, ReplayUsageResult result)
            implements AgentEventPublicMetadata {}

    record SessionRevoked(UUID targetSessionId, UUID workspaceId, Instant occurredAt)
            implements AgentEventPublicMetadata {}

    record OperationalContextChanged(long version, java.util.List<String> changedFields,
            OperationalContextResolutionStatus projectResolutionStatus,
            OperationalContextResolutionStatus workItemResolutionStatus) implements AgentEventPublicMetadata {}
}
