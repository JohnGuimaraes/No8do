package com.no8do.api.replay;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record ReplayResponse(
        UUID id,
        UUID workspaceId,
        UUID projectId,
        String projectName,
        String title,
        ReplayType type,
        String problem,
        String solution,
        String context,
        List<String> tags,
        List<String> stack,
        ReplayStatus status,
        int version,
        int usageCount,
        int successCount,
        int failureCount,
        Instant lastUsedAt,
        UUID createdBy,
        String createdByName,
        Instant createdAt,
        Instant updatedAt
) {
    static ReplayResponse from(Replay replay) {
        return new ReplayResponse(
            replay.getId(),
            replay.getWorkspace().getId(),
            replay.getProject() == null ? null : replay.getProject().getId(),
            replay.getProject() == null ? null : replay.getProject().getName(),
            replay.getTitle(), replay.getType(), replay.getProblem(), replay.getSolution(), replay.getContext(),
            List.copyOf(Arrays.asList(replay.getTags())), List.copyOf(Arrays.asList(replay.getStack())),
            replay.getStatus(), replay.getVersion(),
            replay.getUsageCount(), replay.getSuccessCount(), replay.getFailureCount(), replay.getLastUsedAt(),
            replay.getCreatedBy() == null ? null : replay.getCreatedBy().getId(),
            replay.getCreatedBy() == null ? "Usuário excluído" : replay.getCreatedBy().getName(),
            replay.getCreatedAt(), replay.getUpdatedAt()
        );
    }
}
