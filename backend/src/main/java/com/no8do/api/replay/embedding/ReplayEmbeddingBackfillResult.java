package com.no8do.api.replay.embedding;

public record ReplayEmbeddingBackfillResult(long examined, long processed, long generated, long reused) {}
