package com.no8do.api.replay;

import java.util.UUID;

public record ReplayCompactRepresentation(UUID replayId, int retrievalRank, double hybridScore,
        Integer lexicalRank, Integer vectorRank, String content, boolean truncated) {
    public ReplayCompactRepresentation {
        if (replayId == null) throw new IllegalArgumentException("replayId é obrigatório.");
        if (retrievalRank < 1) throw new IllegalArgumentException("retrievalRank deve ser maior que zero.");
        if (!Double.isFinite(hybridScore)) throw new IllegalArgumentException("hybridScore deve ser finito.");
        if (lexicalRank == null && vectorRank == null) throw new IllegalArgumentException("provenance de retrieval é obrigatória.");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content não pode ser vazio.");
    }
}
