package com.no8do.api.replay;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RegisterReplayUsageRequest(
        UUID projectId,
        @Min(1) @Max(1_000_000) Integer replayVersion,
        @NotNull ReplayUsageResult result,
        @NotNull ReplayUsageSource source,
        @Size(max = 20000) String context,
        Boolean materiallyUsed
) {
    public RegisterReplayUsageRequest(UUID projectId, Integer replayVersion, ReplayUsageResult result,
            ReplayUsageSource source, String context) {
        this(projectId, replayVersion, result, source, context, null);
    }
}
