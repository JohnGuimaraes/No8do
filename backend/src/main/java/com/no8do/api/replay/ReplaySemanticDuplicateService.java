package com.no8do.api.replay;

import com.no8do.api.replay.embedding.EmbeddingProvider;
import com.no8do.api.replay.embedding.ReplayCanonicalizer;
import com.no8do.api.replay.embedding.ReplaySemanticDuplicateQuery;
import com.no8do.api.replay.embedding.ReplayVectorSearchService;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReplaySemanticDuplicateService {
    private static final int MAX_TOP_K = 100;
    private final WorkspaceAuthorizationService authorizationService;
    private final ReplayCanonicalizer canonicalizer;
    private final ReplayVectorSearchService vectorSearch;

    public ReplaySemanticDuplicateService(WorkspaceAuthorizationService authorizationService, ReplayVectorSearchService vectorSearch) {
        this.authorizationService = authorizationService;
        this.canonicalizer = new ReplayCanonicalizer();
        this.vectorSearch = vectorSearch;
    }

    @Transactional(readOnly = true)
    public List<ReplaySemanticDuplicateCandidate> findDuplicates(UUID workspaceId, UUID currentUserId,
            ReplaySemanticDuplicateQuery query, UUID excludeReplayId, EmbeddingProvider provider, int topK, double maxDistance) {
        if (workspaceId == null || currentUserId == null || query == null || provider == null) throw new IllegalArgumentException("workspaceId, currentUserId, query e provider são obrigatórios.");
        if (topK < 1 || topK > MAX_TOP_K) throw new IllegalArgumentException("topK deve estar entre 1 e " + MAX_TOP_K + ".");
        if (!Double.isFinite(maxDistance) || maxDistance < 0D || maxDistance > 2D) throw new IllegalArgumentException("maxDistance deve ser finito e estar entre 0 e 2.");

        authorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        String canonicalContent = canonicalizer.canonicalize(query).text();
        int vectorTopK = excludeReplayId == null ? topK : Math.min(MAX_TOP_K, topK + 1);
        return vectorSearch.search(workspaceId, canonicalContent, provider, vectorTopK).stream()
                .map(hit -> new ReplaySemanticDuplicateCandidate(hit.replayVersion().getReplay().getId(), hit.replayVersion(), hit.distance()))
                .filter(candidate -> !candidate.replayId().equals(excludeReplayId))
                .filter(candidate -> candidate.distance() <= maxDistance)
                .sorted(Comparator.comparingDouble(ReplaySemanticDuplicateCandidate::distance).thenComparing(ReplaySemanticDuplicateCandidate::replayId))
                .limit(topK)
                .toList();
    }
}
