package com.no8do.api.replay.embedding;

import com.no8do.api.replay.ReplayVersion;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReplayEmbeddingService {
    private final ReplayEmbeddingRepository repository;
    private final ReplayEmbeddingPersistence persistence;
    private final ReplayCanonicalizer canonicalizer = new ReplayCanonicalizer();

    public ReplayEmbeddingService(ReplayEmbeddingRepository repository, ReplayEmbeddingPersistence persistence) {
        this.repository = repository;
        this.persistence = persistence;
    }

    @Transactional
    public ReplayEmbeddingOutcome ensureEmbedding(ReplayVersion version, EmbeddingProvider provider) {
        CanonicalReplayContent canonical = canonicalizer.canonicalize(version);
        EmbeddingProviderDescriptor descriptor = provider.descriptor();
        var existing = repository.findByReplayVersionIdAndProviderAndModelAndDimensions(version.getId(), descriptor.provider(), descriptor.model(), descriptor.dimensions());
        if (existing.isPresent() && existing.get().getContentHash().equals(canonical.contentHash())) return new ReplayEmbeddingOutcome(existing.get(), true);
        EmbeddingResult result = provider.embed(canonical.text());
        if (result.dimensions() != descriptor.dimensions()) throw new IllegalStateException("Dimensões retornadas pelo provider não correspondem ao descriptor.");
        ReplayEmbedding embedding = existing.orElseGet(() -> new ReplayEmbedding(version.getWorkspace().getId(), version.getReplay().getId(), version.getId(), descriptor, canonical.contentHash(), result));
        if (existing.isPresent()) embedding.replace(canonical.contentHash(), result);
        try {
            return new ReplayEmbeddingOutcome(persistence.saveAndFlush(embedding), false);
        } catch (DataIntegrityViolationException collision) {
            return resolveConcurrentCollision(version, descriptor, canonical, collision);
        }
    }

    private ReplayEmbeddingOutcome resolveConcurrentCollision(ReplayVersion version, EmbeddingProviderDescriptor descriptor, CanonicalReplayContent canonical, DataIntegrityViolationException collision) {
        return repository.findByReplayVersionIdAndProviderAndModelAndDimensions(version.getId(), descriptor.provider(), descriptor.model(), descriptor.dimensions())
                .filter(winner -> winner.getContentHash().equals(canonical.contentHash()))
                .map(winner -> new ReplayEmbeddingOutcome(winner, true))
                .orElseThrow(() -> new IllegalStateException("Colisão concorrente de embedding com conteúdo divergente.", collision));
    }
}
