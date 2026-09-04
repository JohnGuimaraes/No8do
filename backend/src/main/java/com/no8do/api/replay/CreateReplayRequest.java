package com.no8do.api.replay;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateReplayRequest(
        @NotBlank String title,
        @NotNull ReplayType type,
        String problem,
        String solution,
        String context,
        List<String> tags,
        List<String> stack,
        ReplayStatus status,
        UUID projectId
) {}
