package com.no8do.api.replay;

import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ReplayContextPackage(UUID workspaceId, String query,
        List<ReplayBudgetedContextEntry> compactSources, List<ReplayFullSourceEntry> fullSources,
        List<ReplayContextSource> sourceManifest, ReplayContextTokenAccounting tokenAccounting) {
    public ReplayContextPackage {
        if (workspaceId == null) throw new IllegalArgumentException("workspaceId é obrigatório.");
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query é obrigatória.");
        compactSources = List.copyOf(compactSources);
        fullSources = List.copyOf(fullSources);
        sourceManifest = List.copyOf(sourceManifest);
        if (tokenAccounting == null) throw new IllegalArgumentException("tokenAccounting é obrigatório.");
        Map<UUID, ReplayContextSource> expectedManifest = new HashMap<>();
        long compactTokens = 0;
        for (ReplayBudgetedContextEntry entry : compactSources) {
            ReplayCompactRepresentation compact = entry.representation();
            putUnique(expectedManifest, new ReplayContextSource(compact.replayId(), compact.retrievalRank(),
                    compact.hybridScore(), compact.lexicalRank(), compact.vectorRank(), ReplayContextRepresentationType.COMPACT));
            compactTokens = addExact(compactTokens, entry.estimatedTokens());
        }
        long fullTokens = 0;
        for (ReplayFullSourceEntry entry : fullSources) {
            ReplayFullSourceRepresentation full = entry.representation();
            putUnique(expectedManifest, new ReplayContextSource(full.replayId(), full.retrievalRank(),
                    full.hybridScore(), full.lexicalRank(), full.vectorRank(), ReplayContextRepresentationType.FULL));
            fullTokens = addExact(fullTokens, entry.estimatedTokens());
        }
        if (sourceManifest.size() != expectedManifest.size()) throw new IllegalArgumentException("sourceManifest incompleto ou duplicado.");
        Map<UUID, ReplayContextSource> actualManifest = new HashMap<>();
        ReplayContextSource previous = null;
        for (ReplayContextSource source : sourceManifest) {
            if (actualManifest.putIfAbsent(source.replayId(), source) != null) {
                throw new IllegalArgumentException("sourceManifest contém Replay duplicado.");
            }
            if (previous != null && Comparator.comparingInt(ReplayContextSource::retrievalRank)
                    .thenComparing(item -> item.replayId().toString()).compare(previous, source) > 0) {
                throw new IllegalArgumentException("sourceManifest deve estar ordenado por retrievalRank.");
            }
            previous = source;
        }
        if (!actualManifest.equals(expectedManifest)) throw new IllegalArgumentException("sourceManifest diverge das fontes do pacote.");
        if (compactTokens != tokenAccounting.compactTokens() || fullTokens != tokenAccounting.fullSourceTokens()) {
            throw new IllegalArgumentException("tokenAccounting diverge das fontes do pacote.");
        }
    }

    private static void putUnique(Map<UUID, ReplayContextSource> manifest, ReplayContextSource source) {
        if (manifest.putIfAbsent(source.replayId(), source) != null) {
            throw new IllegalArgumentException("Replay duplicado entre as fontes do pacote.");
        }
    }

    private static long addExact(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("overflow no accounting de tokens.", exception);
        }
    }
}
