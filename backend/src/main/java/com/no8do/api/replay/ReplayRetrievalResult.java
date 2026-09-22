package com.no8do.api.replay;

import java.util.List;
import java.util.UUID;

public record ReplayRetrievalResult(UUID workspaceId, String query, int candidateLimit, int returnedCandidates,
        List<ReplayRetrievalCandidate> candidates) {
    public ReplayRetrievalResult {
        if (workspaceId == null || query == null || query.isBlank()) throw new IllegalArgumentException("workspaceId e query são obrigatórios.");
        if (candidateLimit < 1 || candidateLimit > 100) throw new IllegalArgumentException("candidateLimit deve estar entre 1 e 100.");
        candidates = List.copyOf(candidates);
        if (returnedCandidates != candidates.size() || returnedCandidates > candidateLimit) throw new IllegalArgumentException("quantidade de candidatos inválida.");
    }
}
