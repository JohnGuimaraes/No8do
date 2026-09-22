package com.no8do.api.replay.embedding;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReplayEmbeddingRepository extends JpaRepository<ReplayEmbedding, UUID> {
    Optional<ReplayEmbedding> findByReplayVersionIdAndProviderAndModelAndDimensions(UUID replayVersionId, String provider, String model, int dimensions);

    @Query(value = """
            select embedding.replay_version_id as replayVersionId,
                   embedding.embedding <=> cast(:queryVector as vector) as distance
            from replay_embeddings embedding
            join replay_versions replay_version on replay_version.id = embedding.replay_version_id
            join replays replay on replay.id = replay_version.replay_id
            where embedding.workspace_id = :workspaceId
              and replay_version.workspace_id = :workspaceId
              and replay.workspace_id = :workspaceId
              and replay_version.version = replay.version
              and replay_version.status in ('DRAFT', 'VALIDATED', 'DEPRECATED')
              and embedding.provider = :provider
              and embedding.model = :model
              and embedding.dimensions = :dimensions
            order by embedding.embedding <=> cast(:queryVector as vector) asc
            limit :limit
            """, nativeQuery = true)
    List<ReplayVectorSearchRow> searchCurrentByVector(
            @Param("workspaceId") UUID workspaceId,
            @Param("provider") String provider,
            @Param("model") String model,
            @Param("dimensions") int dimensions,
            @Param("queryVector") String queryVector,
            @Param("limit") int limit);
}
