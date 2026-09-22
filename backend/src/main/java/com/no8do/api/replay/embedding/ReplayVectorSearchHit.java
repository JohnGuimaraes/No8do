package com.no8do.api.replay.embedding;

import com.no8do.api.replay.ReplayVersion;

public record ReplayVectorSearchHit(ReplayVersion replayVersion, double distance) {}
