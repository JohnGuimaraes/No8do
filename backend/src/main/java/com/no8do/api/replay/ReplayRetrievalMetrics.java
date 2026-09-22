package com.no8do.api.replay;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ReplayRetrievalMetrics {
    private ReplayRetrievalMetrics() {}

    /** Precision usa K como denominador; recall usa todos os relevantes; MRR considera somente o top K. */
    public static ReplayRetrievalMetricsResult calculate(List<UUID> ranking, Set<UUID> relevantReplayIds, int k) {
        if (ranking == null || relevantReplayIds == null) throw new IllegalArgumentException("ranking e relevantReplayIds são obrigatórios.");
        if (k < 1) throw new IllegalArgumentException("K deve ser maior que zero.");
        if (relevantReplayIds.isEmpty() || relevantReplayIds.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("relevantReplayIds deve conter IDs não nulos.");
        if (ranking.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("ranking não pode conter ID nulo.");
        if (new HashSet<>(ranking).size() != ranking.size()) throw new IllegalArgumentException("ranking não pode conter Replay ID duplicado.");

        int limit = Math.min(k, ranking.size());
        int relevant = 0;
        int firstRelevantRank = 0;
        double dcg = 0D;
        for (int index = 0; index < limit; index++) {
            if (relevantReplayIds.contains(ranking.get(index))) {
                relevant++;
                int rank = index + 1;
                if (firstRelevantRank == 0) firstRelevantRank = rank;
                dcg += 1D / log2(rank + 1);
            }
        }
        double idcg = 0D;
        for (int rank = 1; rank <= Math.min(k, relevantReplayIds.size()); rank++) idcg += 1D / log2(rank + 1);
        return new ReplayRetrievalMetricsResult((double) relevant / k, (double) relevant / relevantReplayIds.size(),
                firstRelevantRank == 0 ? 0D : 1D / firstRelevantRank, dcg / idcg);
    }

    private static double log2(int value) { return Math.log(value) / Math.log(2D); }
}
