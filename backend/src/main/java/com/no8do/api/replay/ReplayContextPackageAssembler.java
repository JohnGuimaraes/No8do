package com.no8do.api.replay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pure consolidation of the already retrieved, budgeted, and expanded Replay sources. */
public final class ReplayContextPackageAssembler {
    public ReplayContextPackage assemble(ReplayRetrievalResult retrieval,
            ReplayBudgetedContextResult budgeted, ReplaySourceExpansionResult expansion) {
        if (retrieval == null) throw new IllegalArgumentException("retrieval é obrigatório.");
        if (budgeted == null) throw new IllegalArgumentException("budgeted é obrigatório.");
        if (expansion == null) throw new IllegalArgumentException("expansion é obrigatório.");
        if (retrieval.workspaceId() == null) throw new IllegalArgumentException("workspaceId do retrieval é obrigatório.");

        Map<SourceKey, ReplayRetrievalCandidate> retrievalCandidates = indexRetrieval(retrieval);
        Map<UUID, ReplayBudgetedContextEntry> selectedById = new HashMap<>();
        Set<UUID> selectedIds = new HashSet<>();
        long originalCompactTokens = 0;
        for (ReplayBudgetedContextEntry entry : budgeted.selectedEntries()) {
            ReplayCompactRepresentation compact = entry.representation();
            if (!selectedIds.add(compact.replayId())) throw invalid("compact selecionado duplicado.");
            ReplayRetrievalCandidate candidate = retrievalCandidates.get(new SourceKey(compact.replayId(), compact.retrievalRank()));
            if (candidate == null) throw invalid("compact selecionado não pertence ao retrieval.");
            requireProvenance(compact.replayId(), compact.retrievalRank(), compact.hybridScore(),
                    compact.lexicalRank(), compact.vectorRank(), candidate.hybridScore(), candidate.lexicalRank(),
                    candidate.vectorRank());
            selectedById.put(compact.replayId(), entry);
            originalCompactTokens = addExact(originalCompactTokens, entry.estimatedTokens());
        }
        if (originalCompactTokens != budgeted.usedTokens()) throw invalid("usedTokens não corresponde aos compactos selecionados.");

        Set<UUID> expandedIds = new HashSet<>();
        long fullSourceTokens = 0;
        List<ReplayContextSource> manifest = new ArrayList<>();
        for (ReplayFullSourceEntry entry : expansion.selectedSources()) {
            ReplayFullSourceRepresentation full = entry.representation();
            if (!expandedIds.add(full.replayId())) throw invalid("full source duplicada.");
            ReplayBudgetedContextEntry compactEntry = selectedById.get(full.replayId());
            if (compactEntry == null) throw invalid("full source não corresponde a compact selecionado.");
            ReplayCompactRepresentation compact = compactEntry.representation();
            if (full.retrievalRank() != compact.retrievalRank()) throw invalid("retrievalRank da full source diverge do compacto.");
            requireProvenance(full.replayId(), full.retrievalRank(), full.hybridScore(), full.lexicalRank(),
                    full.vectorRank(), compact.hybridScore(), compact.lexicalRank(), compact.vectorRank());
            ReplayRetrievalCandidate candidate = retrievalCandidates.get(new SourceKey(full.replayId(), full.retrievalRank()));
            if (candidate == null) throw invalid("full source não pertence ao retrieval.");
            requireProvenance(full.replayId(), full.retrievalRank(), full.hybridScore(), full.lexicalRank(),
                    full.vectorRank(), candidate.hybridScore(), candidate.lexicalRank(), candidate.vectorRank());
            fullSourceTokens = addExact(fullSourceTokens, entry.estimatedTokens());
            manifest.add(source(full, ReplayContextRepresentationType.FULL));
        }

        List<ReplayBudgetedContextEntry> compactSources = budgeted.selectedEntries().stream()
                .filter(entry -> !expandedIds.contains(entry.representation().replayId())).toList();
        long compactTokens = 0;
        for (ReplayBudgetedContextEntry entry : compactSources) {
            ReplayCompactRepresentation compact = entry.representation();
            compactTokens = addExact(compactTokens, entry.estimatedTokens());
            manifest.add(source(compact, ReplayContextRepresentationType.COMPACT));
        }
        long replacedCompactTokens = originalCompactTokens - compactTokens;
        long totalKnowledgeTokens = addExact(compactTokens, fullSourceTokens);
        ReplayContextTokenAccounting accounting = new ReplayContextTokenAccounting(compactTokens, fullSourceTokens,
                totalKnowledgeTokens, budgeted.budget().availableTokens(), expansion.policy().maxExpansionTokens(),
                originalCompactTokens, replacedCompactTokens);
        manifest.sort(Comparator.comparingInt(ReplayContextSource::retrievalRank)
                .thenComparing(source -> source.replayId().toString()));
        return new ReplayContextPackage(retrieval.workspaceId(), retrieval.query(), compactSources,
                expansion.selectedSources(), manifest, accounting);
    }

    private Map<SourceKey, ReplayRetrievalCandidate> indexRetrieval(ReplayRetrievalResult retrieval) {
        Map<SourceKey, ReplayRetrievalCandidate> candidates = new HashMap<>();
        for (ReplayRetrievalCandidate candidate : retrieval.candidates()) {
            SourceKey key = new SourceKey(candidate.replayId(), candidate.retrievalRank());
            if (candidates.putIfAbsent(key, candidate) != null) throw invalid("candidato de retrieval duplicado.");
        }
        return candidates;
    }

    private ReplayContextSource source(ReplayCompactRepresentation compact, ReplayContextRepresentationType type) {
        return new ReplayContextSource(compact.replayId(), compact.retrievalRank(), compact.hybridScore(),
                compact.lexicalRank(), compact.vectorRank(), type);
    }

    private ReplayContextSource source(ReplayFullSourceRepresentation full, ReplayContextRepresentationType type) {
        return new ReplayContextSource(full.replayId(), full.retrievalRank(), full.hybridScore(),
                full.lexicalRank(), full.vectorRank(), type);
    }

    private void requireProvenance(UUID id, int rank, double score, Integer lexical, Integer vector,
            double expectedScore, Integer expectedLexical, Integer expectedVector) {
        if (Double.compare(score, expectedScore) != 0 || !java.util.Objects.equals(lexical, expectedLexical)
                || !java.util.Objects.equals(vector, expectedVector)) {
            throw invalid("provenance diverge para replay " + id + " no retrievalRank " + rank + ".");
        }
    }

    private long addExact(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("overflow no accounting de tokens.", exception);
        }
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private record SourceKey(UUID replayId, int retrievalRank) { }
}
