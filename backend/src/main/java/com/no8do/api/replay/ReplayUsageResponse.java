package com.no8do.api.replay;

import java.time.Instant;
import java.util.UUID;

public record ReplayUsageResponse(
        UUID id,
        UUID replayId,
        UUID projectId,
        String projectName,
        UUID usedBy,
        String usedByName,
        int replayVersion,
        ReplayUsageResult result,
        ReplayUsageSource source,
        String context,
        Instant usedAt
) {
    static ReplayUsageResponse from(ReplayUsage usage) {
        return new ReplayUsageResponse(
            usage.getId(),
            usage.getReplay().getId(),
            usage.getProject() == null ? null : usage.getProject().getId(),
            usage.getProject() == null ? null : usage.getProject().getName(),
            usage.getUsedBy() == null ? null : usage.getUsedBy().getId(),
            usage.getUsedBy() == null ? "Usuário excluído" : usage.getUsedBy().getName(),
            usage.getReplayVersion(),
            usage.getResult(),
            usage.getSource(),
            usage.getContext(),
            usage.getUsedAt()
        );
    }
}
