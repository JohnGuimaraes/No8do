package com.no8do.api.replay.embedding;

import java.util.UUID;

public interface ReplayVectorSearchRow {
    UUID getReplayVersionId();
    double getDistance();
}
