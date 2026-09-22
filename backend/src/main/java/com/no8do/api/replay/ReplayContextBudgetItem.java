package com.no8do.api.replay;

import java.util.UUID;

/** Replay identity and externally estimated token cost; contains no Replay content. */
public record ReplayContextBudgetItem(UUID replayId, int retrievalRank, int estimatedTokens) {
    public ReplayContextBudgetItem {
        if (replayId == null) throw new IllegalArgumentException("replayId é obrigatório.");
        if (retrievalRank < 1) throw new IllegalArgumentException("retrievalRank deve ser maior que zero.");
        if (estimatedTokens <= 0) throw new IllegalArgumentException("estimatedTokens deve ser maior que zero.");
    }
}
