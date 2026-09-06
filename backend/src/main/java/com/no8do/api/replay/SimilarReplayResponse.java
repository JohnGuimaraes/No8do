package com.no8do.api.replay;

import java.util.List;
import java.util.UUID;

public record SimilarReplayResponse(UUID id, String title, ReplayType type, ReplayStatus status, int version, List<String> stack, int usageCount, int score) {
    static SimilarReplayResponse from(Replay replay, int score) {
        return new SimilarReplayResponse(replay.getId(), replay.getTitle(), replay.getType(), replay.getStatus(), replay.getVersion(), List.of(replay.getStack()), replay.getUsageCount(), score);
    }
}
