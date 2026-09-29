package com.no8do.api.agent;

import java.time.Instant;
import java.util.UUID;

record OperationalContextSnapshot(UUID sessionId, UUID userId, UUID workspaceId,
        Instant registeredAt, Instant lastSeenAt, Instant lastActivityAt, Instant disconnectedAt, Instant revokedAt,
        Long contextVersion, String signalHash, AgentOperationalContextSignal.Repository repository,
        String branch, String workingDirectory,
        java.util.List<AgentOperationalContextSignal.Reference> references, String projectResolutionRepositoryId) {
    OperationalContextSnapshot {
        references = java.util.List.copyOf(references);
    }

    boolean hasContext() { return contextVersion != null; }
}
