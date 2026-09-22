package com.no8do.api.replay;

import com.no8do.api.replay.embedding.EmbeddingProvider;
import com.no8do.api.replay.embedding.ReplayVectorSearchService;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReplayRetrievalEvaluationService {
    private final ReplayService replayService;
    private final ReplayVectorSearchService vectorSearch;
    private final ReplayHybridSearchService hybridSearch;

    public ReplayRetrievalEvaluationService(ReplayService replayService, ReplayVectorSearchService vectorSearch, ReplayHybridSearchService hybridSearch) {
        this.replayService = replayService; this.vectorSearch = vectorSearch; this.hybridSearch = hybridSearch;
    }

    public ReplayRetrievalEvaluationReport evaluate(UUID workspaceId, UUID currentUserId, List<ReplayRetrievalEvaluationCase> cases,
            EmbeddingProvider provider, int k) {
        if (workspaceId == null || currentUserId == null || provider == null) throw new IllegalArgumentException("workspaceId, currentUserId e provider são obrigatórios.");
        if (cases == null || cases.isEmpty()) throw new IllegalArgumentException("cases não pode ser vazia.");
        if (k < 1 || k > 100) throw new IllegalArgumentException("K deve estar entre 1 e 100.");
        EnumMap<ReplayRetrievalStrategy, List<ReplayRetrievalEvaluationCaseResult>> results = new EnumMap<>(ReplayRetrievalStrategy.class);
        for (ReplayRetrievalStrategy strategy : ReplayRetrievalStrategy.values()) results.put(strategy, new ArrayList<>());

        for (ReplayRetrievalEvaluationCase evaluationCase : cases) {
            // Esta chamada preserva o gateway de autorização antes de qualquer busca vetorial.
            List<ReplayResponse> lexical = replayService.search(workspaceId, currentUserId, evaluationCase.query());
            add(results, ReplayRetrievalStrategy.LEXICAL, evaluationCase, lexical.stream().limit(k).map(ReplayResponse::id).toList(), k);
            add(results, ReplayRetrievalStrategy.VECTOR, evaluationCase, vectorSearch.search(workspaceId, evaluationCase.query(), provider, k).stream()
                    .map(hit -> hit.replayVersion().getReplay().getId()).toList(), k);
            add(results, ReplayRetrievalStrategy.HYBRID, evaluationCase, hybridSearch.search(workspaceId, currentUserId, evaluationCase.query(), provider, k).stream()
                    .map(ReplayHybridSearchHit::replayId).toList(), k);
        }
        EnumMap<ReplayRetrievalStrategy, ReplayRetrievalEvaluationResult> report = new EnumMap<>(ReplayRetrievalStrategy.class);
        results.forEach((strategy, values) -> report.put(strategy, ReplayRetrievalEvaluationResult.aggregate(strategy, k, values)));
        return new ReplayRetrievalEvaluationReport(report);
    }

    private void add(EnumMap<ReplayRetrievalStrategy, List<ReplayRetrievalEvaluationCaseResult>> results, ReplayRetrievalStrategy strategy,
            ReplayRetrievalEvaluationCase evaluationCase, List<UUID> ranking, int k) {
        results.get(strategy).add(new ReplayRetrievalEvaluationCaseResult(evaluationCase.label(), ReplayRetrievalMetrics.calculate(ranking, evaluationCase.relevantReplayIds(), k)));
    }
}
