package com.no8do.api.replay.embedding;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplayEmbeddingRepository extends JpaRepository<ReplayEmbedding, UUID> {
    Optional<ReplayEmbedding> findByReplayVersionIdAndProviderAndModelAndDimensions(UUID replayVersionId, String provider, String model, int dimensions);
}
