package com.no8do.api.replay;

import java.util.UUID;

/** Stable provenance reference for one source included in rendered context. */
public record ReplayRenderedSource(String sourceLabel, UUID replayId, int retrievalRank,
        ReplayContextRepresentationType representationType, double hybridScore,
        Integer lexicalRank, Integer vectorRank) {
    public ReplayRenderedSource {
        if (sourceLabel == null || sourceLabel.isBlank()) throw new IllegalArgumentException("sourceLabel é obrigatório.");
        if (replayId == null) throw new IllegalArgumentException("replayId é obrigatório.");
        if (retrievalRank < 1) throw new IllegalArgumentException("retrievalRank deve ser maior que zero.");
        if (representationType == null) throw new IllegalArgumentException("representationType é obrigatório.");
        if (!Double.isFinite(hybridScore)) throw new IllegalArgumentException("hybridScore deve ser finito.");
        if (lexicalRank == null && vectorRank == null) throw new IllegalArgumentException("provenance de retrieval é obrigatória.");
    }
}
