package com.no8do.api.replay;

import java.util.UUID;

public record ReplayContextSource(UUID replayId, int retrievalRank, double hybridScore,
        Integer lexicalRank, Integer vectorRank, ReplayContextRepresentationType representationType) {
    public ReplayContextSource {
        if (replayId == null) throw new IllegalArgumentException("replayId é obrigatório.");
        if (retrievalRank < 1) throw new IllegalArgumentException("retrievalRank deve ser maior que zero.");
        if (!Double.isFinite(hybridScore)) throw new IllegalArgumentException("hybridScore deve ser finito.");
        if (lexicalRank == null && vectorRank == null) throw new IllegalArgumentException("provenance de retrieval é obrigatória.");
        if (representationType == null) throw new IllegalArgumentException("representationType é obrigatório.");
    }
}
