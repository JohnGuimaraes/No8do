package com.no8do.api.replay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Selects full sources from the already-budgeted compact entries without additional retrieval. */
public final class ReplaySourceExpansionPlanner {
    private final ReplayFullSourceFactory fullSourceFactory = new ReplayFullSourceFactory();

    public ReplaySourceExpansionResult expand(ReplayRetrievalResult retrieval,
            ReplayBudgetedContextResult compactContext, ReplaySourceExpansionPolicy policy,
            TextTokenEstimator tokenEstimator) {
        if (retrieval == null) throw new IllegalArgumentException("retrieval é obrigatório.");
        if (compactContext == null) throw new IllegalArgumentException("compactContext é obrigatório.");
        if (policy == null) throw new IllegalArgumentException("policy é obrigatória.");
        if (tokenEstimator == null) throw new IllegalArgumentException("tokenEstimator é obrigatório.");

        Map<SourceKey, ReplayRetrievalCandidate> candidates = candidatesByIdentity(retrieval);
        List<ReplayBudgetedContextEntry> eligible = compactContext.selectedEntries().stream()
                .sorted(Comparator.comparingInt(entry -> entry.representation().retrievalRank())).toList();
        if (eligible.isEmpty()) return new ReplaySourceExpansionResult(policy, List.of(), List.of(), 0, policy.maxExpansionTokens());

        List<ReplayFullSourceEntry> selected = new ArrayList<>();
        List<ReplaySkippedFullSource> skipped = new ArrayList<>();
        int used = 0;
        for (ReplayBudgetedContextEntry compact : eligible) {
            ReplayCompactRepresentation compactRepresentation = compact.representation();
            SourceKey key = new SourceKey(compactRepresentation.replayId(), compactRepresentation.retrievalRank());
            ReplayRetrievalCandidate candidate = candidates.get(key);
            if (candidate == null) throw new IllegalArgumentException("Compact entry sem candidato de retrieval correspondente.");
            ReplayFullSourceRepresentation full = fullSourceFactory.full(candidate);

            if (selected.size() >= policy.maxFullSources()) {
                skipped.add(new ReplaySkippedFullSource(full, null, ReplaySourceSkipReason.SOURCE_LIMIT));
                continue;
            }

            int estimated = tokenEstimator.estimate(full.content());
            if (estimated <= 0) throw new IllegalArgumentException("tokenEstimator deve retornar valor maior que zero.");
            if (estimated <= policy.maxExpansionTokens() - used) {
                selected.add(new ReplayFullSourceEntry(full, estimated));
                used += estimated;
            } else {
                skipped.add(new ReplaySkippedFullSource(full, estimated, ReplaySourceSkipReason.TOKEN_BUDGET));
            }
        }
        return new ReplaySourceExpansionResult(policy, selected, skipped, used, policy.maxExpansionTokens() - used);
    }

    private Map<SourceKey, ReplayRetrievalCandidate> candidatesByIdentity(ReplayRetrievalResult retrieval) {
        Map<SourceKey, ReplayRetrievalCandidate> result = new HashMap<>();
        for (ReplayRetrievalCandidate candidate : retrieval.candidates()) {
            SourceKey key = new SourceKey(candidate.replayId(), candidate.retrievalRank());
            if (result.put(key, candidate) != null) throw new IllegalArgumentException("Candidatos de retrieval duplicados.");
        }
        return result;
    }

    private record SourceKey(UUID replayId, int retrievalRank) { }
}
