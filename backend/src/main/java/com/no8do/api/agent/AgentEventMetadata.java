package com.no8do.api.agent;

import com.no8do.api.replay.ReplayUsageResult;
import java.time.Instant;
import java.util.UUID;

public sealed interface AgentEventMetadata permits AgentEventMetadata.Empty,
        AgentEventMetadata.RuntimeModeChanged, AgentEventMetadata.CapabilityDenied,
        AgentEventMetadata.PolicyDenied, AgentEventMetadata.ReplayUsageRecorded,
        AgentEventMetadata.SessionRevoked {

    record Empty() implements AgentEventMetadata {}

    record RuntimeModeChanged(AgentRuntimeMode previousMode, AgentRuntimeMode newMode)
            implements AgentEventMetadata {
        public RuntimeModeChanged {
            if (previousMode == null || newMode == null) {
                throw new IllegalArgumentException("Os modos anterior e novo são obrigatórios.");
            }
        }
    }

    record CapabilityDenied(AgentCapability requiredCapability, AgentRuntimeMode runtimeMode)
            implements AgentEventMetadata {
        public CapabilityDenied {
            if (requiredCapability == null || runtimeMode == null) {
                throw new IllegalArgumentException("Capability e runtime mode são obrigatórios.");
            }
        }
    }

    record PolicyDenied(String policyId, String reason) implements AgentEventMetadata {
        public PolicyDenied {
            if (policyId == null || policyId.isBlank() || reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Policy e motivo seguro são obrigatórios.");
            }
        }
    }

    record ReplayUsageRecorded(UUID replayId, int replayVersion, ReplayUsageResult result)
            implements AgentEventMetadata {
        public ReplayUsageRecorded {
            if (replayId == null || replayVersion < 1 || result == null) {
                throw new IllegalArgumentException("Replay, versão e resultado são obrigatórios.");
            }
        }
    }

    record SessionRevoked(UUID targetSessionId, UUID actorUserId, UUID workspaceId, Instant occurredAt)
            implements AgentEventMetadata {
        public SessionRevoked {
            if (targetSessionId == null || actorUserId == null || occurredAt == null) {
                throw new IllegalArgumentException("Sessão-alvo, ator e instante da revogação são obrigatórios.");
            }
        }
    }
}
