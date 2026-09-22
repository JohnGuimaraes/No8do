package com.no8do.api.replay.embedding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "replay_embeddings")
@Getter
@NoArgsConstructor
public class ReplayEmbedding {
    @Id private UUID id;
    @Column(name = "workspace_id", nullable = false) private UUID workspaceId;
    @Column(name = "replay_id", nullable = false) private UUID replayId;
    @Column(name = "replay_version_id", nullable = false) private UUID replayVersionId;
    @Column(nullable = false, length = 100) private String provider;
    @Column(nullable = false, length = 180) private String model;
    @Column(nullable = false) private int dimensions;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "content_hash", nullable = false, length = 64, columnDefinition = "char(64)") private String contentHash;
    @JdbcTypeCode(SqlTypes.VECTOR) @Column(nullable = false, columnDefinition = "vector") private float[] embedding;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public ReplayEmbedding(UUID workspaceId, UUID replayId, UUID replayVersionId, EmbeddingProviderDescriptor descriptor, String contentHash, EmbeddingResult result) {
        if (result.dimensions() != descriptor.dimensions()) throw new IllegalStateException("Dimensões do resultado não correspondem ao descriptor.");
        this.workspaceId = workspaceId; this.replayId = replayId; this.replayVersionId = replayVersionId;
        this.provider = descriptor.provider(); this.model = descriptor.model(); this.dimensions = descriptor.dimensions();
        this.contentHash = contentHash; this.embedding = result.vector();
    }

    public void replace(String contentHash, EmbeddingResult result) {
        if (result.dimensions() != dimensions) throw new IllegalStateException("Dimensões do resultado não correspondem ao registro.");
        this.contentHash = contentHash; this.embedding = result.vector();
    }

    @PrePersist void prePersist() { if (id == null) id = UUID.randomUUID(); if (createdAt == null) createdAt = Instant.now(); }
}
