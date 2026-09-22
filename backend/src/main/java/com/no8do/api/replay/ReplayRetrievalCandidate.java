package com.no8do.api.replay;

import java.util.UUID;

public record ReplayRetrievalCandidate(UUID replayId, ReplayResponse replay, ReplayVersion replayVersion,
        double hybridScore, Integer lexicalRank, Integer vectorRank, int retrievalRank) {
    public ReplayRetrievalCandidate {
        if (replayId == null) throw new IllegalArgumentException("replayId é obrigatório.");
        if (!Double.isFinite(hybridScore)) throw new IllegalArgumentException("hybridScore deve ser finito.");
        if (retrievalRank < 1) throw new IllegalArgumentException("retrievalRank deve ser maior que zero.");
        if (lexicalRank == null && vectorRank == null) throw new IllegalArgumentException("candidato deve possuir origem de retrieval.");
    }
}
