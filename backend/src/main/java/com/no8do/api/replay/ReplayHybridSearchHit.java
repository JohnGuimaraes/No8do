package com.no8do.api.replay;

/**
 * Resultado explicável da busca híbrida. Em candidatos vindos apenas de uma
 * fonte, o objeto da outra fonte é nulo.
 */
public record ReplayHybridSearchHit(
        java.util.UUID replayId,
        ReplayResponse replay,
        ReplayVersion replayVersion,
        double hybridScore,
        Integer lexicalRank,
        Integer vectorRank
) {}
