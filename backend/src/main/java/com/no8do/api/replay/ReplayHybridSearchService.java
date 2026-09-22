package com.no8do.api.replay;

import com.no8do.api.replay.embedding.EmbeddingProvider;
import com.no8do.api.replay.embedding.ReplayVectorSearchHit;
import com.no8do.api.replay.embedding.ReplayVectorSearchService;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReplayHybridSearchService {
    static final int RRF_K = 60;
    private static final int MAX_TOP_K = 100;
    private static final int MAX_CANDIDATES = 100;

    private final ReplayService replayService;
    private final ReplayVectorSearchService vectorSearch;

    public ReplayHybridSearchService(ReplayService replayService, ReplayVectorSearchService vectorSearch) {
        this.replayService = replayService;
        this.vectorSearch = vectorSearch;
    }

    @Transactional(readOnly = true)
    public List<ReplayHybridSearchHit> search(UUID workspaceId, UUID currentUserId, String query,
            EmbeddingProvider provider, int topK) {
        if (workspaceId == null) throw new IllegalArgumentException("workspaceId não pode ser nulo.");
        if (currentUserId == null) throw new IllegalArgumentException("currentUserId não pode ser nulo.");
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query não pode ser vazia.");
        if (topK < 1 || topK > MAX_TOP_K) throw new IllegalArgumentException("topK deve estar entre 1 e " + MAX_TOP_K + ".");

        int candidateLimit = candidateLimit(topK);
        // A busca lexical também é o portão de autorização do workspace. Não mova esta chamada após a vetorial.
        List<ReplayResponse> lexical = replayService.search(workspaceId, currentUserId, query);

        Map<UUID, Candidate> candidates = new LinkedHashMap<>();
        for (int index = 0; index < Math.min(candidateLimit, lexical.size()); index++) {
            ReplayResponse replay = lexical.get(index);
            candidates.computeIfAbsent(replay.id(), Candidate::new).setLexical(replay, index + 1);
        }

        List<ReplayVectorSearchHit> vector = vectorSearch.search(workspaceId, query, provider, candidateLimit);
        for (int index = 0; index < vector.size(); index++) {
            ReplayVectorSearchHit hit = vector.get(index);
            UUID replayId = hit.replayVersion().getReplay().getId();
            candidates.computeIfAbsent(replayId, Candidate::new).setVector(hit.replayVersion(), index + 1);
        }

        return candidates.values().stream()
                .map(Candidate::toHit)
                .sorted(Comparator.comparingDouble(ReplayHybridSearchHit::hybridScore).reversed()
                        .thenComparing(ReplayHybridSearchHit::lexicalRank, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ReplayHybridSearchHit::vectorRank, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ReplayHybridSearchHit::replayId))
                .limit(topK)
                .toList();
    }

    static int candidateLimit(int topK) {
        return Math.min(MAX_CANDIDATES, Math.max(20, topK * 4));
    }

    private static final class Candidate {
        private final UUID replayId;
        private ReplayResponse replay;
        private ReplayVersion replayVersion;
        private Integer lexicalRank;
        private Integer vectorRank;

        private Candidate(UUID replayId) { this.replayId = replayId; }
        private void setLexical(ReplayResponse value, int rank) { replay = value; lexicalRank = rank; }
        private void setVector(ReplayVersion value, int rank) { replayVersion = value; vectorRank = rank; }
        private ReplayHybridSearchHit toHit() {
            double score = score(lexicalRank) + score(vectorRank);
            return new ReplayHybridSearchHit(replayId, replay, replayVersion, score, lexicalRank, vectorRank);
        }
        private static double score(Integer rank) { return rank == null ? 0D : 1D / (RRF_K + rank); }
    }
}
