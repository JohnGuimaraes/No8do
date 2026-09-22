package com.no8do.api.replay.embedding;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReplayEmbeddingPersistence {

    private final ReplayEmbeddingRepository repository;

    ReplayEmbeddingPersistence(ReplayEmbeddingRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    ReplayEmbedding saveAndFlush(ReplayEmbedding embedding) {
        return repository.saveAndFlush(embedding);
    }
}
