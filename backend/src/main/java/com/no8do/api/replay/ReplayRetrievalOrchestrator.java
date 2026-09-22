package com.no8do.api.replay;

import com.no8do.api.replay.embedding.EmbeddingProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Entrada estável de retrieval; Context Budget e montagem de contexto pertencem às próximas fases. */
@Service
public class ReplayRetrievalOrchestrator {
    private final ReplayHybridSearchService hybridSearch;

    public ReplayRetrievalOrchestrator(ReplayHybridSearchService hybridSearch) { this.hybridSearch = hybridSearch; }

    @Transactional(readOnly = true)
    public ReplayRetrievalResult retrieve(UUID workspaceId, UUID currentUserId, String query, EmbeddingProvider provider,
            int candidateLimit) {
        if (workspaceId == null || currentUserId == null || provider == null) throw new IllegalArgumentException("workspaceId, currentUserId e provider são obrigatórios.");
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query não pode ser vazia.");
        if (candidateLimit < 1 || candidateLimit > 100) throw new IllegalArgumentException("candidateLimit deve estar entre 1 e 100.");
        List<ReplayHybridSearchHit> hits = hybridSearch.search(workspaceId, currentUserId, query, provider, candidateLimit);
        List<ReplayRetrievalCandidate> candidates = java.util.stream.IntStream.range(0, hits.size())
                .mapToObj(index -> candidate(hits.get(index), index + 1))
                .toList();
        return new ReplayRetrievalResult(workspaceId, query, candidateLimit, candidates.size(), candidates);
    }

    private ReplayRetrievalCandidate candidate(ReplayHybridSearchHit hit, int retrievalRank) {
        return new ReplayRetrievalCandidate(hit.replayId(), hit.replay(), hit.replayVersion(), hit.hybridScore(), hit.lexicalRank(), hit.vectorRank(), retrievalRank);
    }
}
