package com.no8do.api.replay;

/** estimatedTokens is null only when SOURCE_LIMIT avoids unnecessary estimation. */
public record ReplaySkippedFullSource(ReplayFullSourceRepresentation representation, Integer estimatedTokens,
        ReplaySourceSkipReason reason) {
    public ReplaySkippedFullSource {
        if (representation == null) throw new IllegalArgumentException("representation é obrigatória.");
        if (reason == null) throw new IllegalArgumentException("reason é obrigatória.");
        if (reason == ReplaySourceSkipReason.TOKEN_BUDGET && (estimatedTokens == null || estimatedTokens <= 0)) {
            throw new IllegalArgumentException("TOKEN_BUDGET exige estimatedTokens positivo.");
        }
        if (reason == ReplaySourceSkipReason.SOURCE_LIMIT && estimatedTokens != null && estimatedTokens <= 0) {
            throw new IllegalArgumentException("estimatedTokens deve ser positivo quando informado.");
        }
    }
}
