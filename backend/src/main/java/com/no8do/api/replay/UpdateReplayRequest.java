package com.no8do.api.replay;

import java.util.List;
import java.util.UUID;

public record UpdateReplayRequest(
        String title,
        ReplayType type,
        String problem,
        String solution,
        String context,
        List<String> tags,
        List<String> stack,
        ReplayStatus status,
        UUID projectId
) {}
